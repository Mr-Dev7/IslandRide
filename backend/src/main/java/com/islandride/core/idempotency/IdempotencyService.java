package com.islandride.core.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.islandride.core.exception.BusinessRuleException;
import com.islandride.core.exception.ConflictException;
import com.islandride.core.exception.IdempotencyConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Concurrency-safe API and domain command idempotency engine backed by MySQL unique keys
 * and deterministic SHA-256 request payload digests.
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final int MAX_KEY_LENGTH = 128;

    private final IdempotencyRecordRepository repository;
    private final TransactionTemplate requiresNewTx;
    private final ObjectMapper canonicalMapper;

    public IdempotencyService(IdempotencyRecordRepository repository,
                              PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.canonicalMapper = new ObjectMapper().findAndRegisterModules();
        this.canonicalMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        this.canonicalMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        this.canonicalMapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
    }

    public record ExecutionOutcome<T>(boolean replayed, int httpStatus, String resourcePublicId, T body) {}

    public record RecordedPayload<T>(int httpStatus, String resourcePublicId, T body) {}

    public static String actorKeyForUser(Long userId) {
        return userId != null ? "usr:" + userId : "system";
    }

    public String normalizeAndValidateKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessRuleException("Idempotency-Key is required for this operation.");
        }
        String trimmed = idempotencyKey.trim();
        if (trimmed.length() > MAX_KEY_LENGTH) {
            throw new BusinessRuleException("Idempotency-Key must not exceed " + MAX_KEY_LENGTH + " characters.");
        }
        return trimmed;
    }

    public <T> ExecutionOutcome<T> execute(String scope,
                                           String actorKey,
                                           String idempotencyKey,
                                           Object requestPayload,
                                           Duration ttl,
                                           Class<T> responseType,
                                           Supplier<RecordedPayload<T>> action) {
        String cleanScope = normalizeScope(scope);
        String cleanActor = (actorKey == null || actorKey.isBlank()) ? "system" : actorKey.trim();
        String cleanKey = normalizeAndValidateKey(idempotencyKey);
        String requestHash = hashPayload(requestPayload);
        Duration effectiveTtl = (ttl == null || ttl.isNegative() || ttl.isZero()) ? Duration.ofHours(24) : ttl;

        Optional<ExecutionOutcome<T>> existingReplay = acquireSlot(cleanScope, cleanActor, cleanKey, requestHash, effectiveTtl, responseType);
        if (existingReplay.isPresent()) {
            return existingReplay.get();
        }

        try {
            RecordedPayload<T> result = action.get();
            if (result == null) {
                throw new IllegalStateException("Idempotent action returned null payload.");
            }
            markCompleted(cleanScope, cleanActor, cleanKey, result.httpStatus(), result.resourcePublicId(), result.body(), effectiveTtl);
            return new ExecutionOutcome<>(false, result.httpStatus(), result.resourcePublicId(), result.body());
        } catch (RuntimeException ex) {
            markFailed(cleanScope, cleanActor, cleanKey);
            throw ex;
        }
    }

    public String hashPayload(Object payload) {
        try {
            String json = payload == null ? "null" : canonicalMapper.writeValueAsString(payload);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Unable to compute idempotency request hash.", ex);
        }
    }

    private <T> Optional<ExecutionOutcome<T>> acquireSlot(String scope,
                                                          String actorKey,
                                                          String key,
                                                          String requestHash,
                                                          Duration ttl,
                                                          Class<T> responseType) {
        try {
            return requiresNewTx.execute(status -> {
                Instant now = Instant.now();
                Optional<IdempotencyRecord> existingOpt = repository.findByScopeAndActorKeyAndIdempotencyKey(scope, actorKey, key);
                if (existingOpt.isPresent()) {
                    IdempotencyRecord existing = existingOpt.get();
                    boolean expired = existing.getExpiresAt() != null && existing.getExpiresAt().isBefore(now);
                    if (!expired && existing.getStatus() == IdempotencyStatus.COMPLETED) {
                        assertSameHash(existing, requestHash);
                        return Optional.of(deserializeReplay(existing, responseType));
                    }
                    if (!expired && existing.getStatus() == IdempotencyStatus.IN_PROGRESS) {
                        assertSameHash(existing, requestHash);
                        throw new ConflictException("A request with this Idempotency-Key is currently being processed.");
                    }
                    // Allow clean retry when previously FAILED or expired
                    existing.setRequestHash(requestHash);
                    existing.setStatus(IdempotencyStatus.IN_PROGRESS);
                    existing.setHttpStatus(null);
                    existing.setResourcePublicId(null);
                    existing.setResponseBody(null);
                    existing.setExpiresAt(now.plus(ttl));
                    repository.save(existing);
                    return Optional.empty();
                }

                IdempotencyRecord record = new IdempotencyRecord();
                record.setScope(scope);
                record.setActorKey(actorKey);
                record.setIdempotencyKey(key);
                record.setRequestHash(requestHash);
                record.setStatus(IdempotencyStatus.IN_PROGRESS);
                record.setExpiresAt(now.plus(ttl));
                repository.saveAndFlush(record);
                return Optional.empty();
            });
        } catch (DataIntegrityViolationException raceEx) {
            // Another thread inserted the same (scope, actorKey, idempotencyKey) concurrently
            return requiresNewTx.execute(status -> {
                Instant now = Instant.now();
                IdempotencyRecord winner = repository.findByScopeAndActorKeyAndIdempotencyKey(scope, actorKey, key)
                        .orElseThrow(() -> new ConflictException("Concurrent idempotency conflict; please retry."));
                boolean expired = winner.getExpiresAt() != null && winner.getExpiresAt().isBefore(now);
                if (!expired && winner.getStatus() == IdempotencyStatus.COMPLETED) {
                    assertSameHash(winner, requestHash);
                    return Optional.of(deserializeReplay(winner, responseType));
                }
                if (!expired && winner.getStatus() == IdempotencyStatus.IN_PROGRESS) {
                    assertSameHash(winner, requestHash);
                    throw new ConflictException("A concurrent request with this Idempotency-Key is currently being processed.");
                }
                // Reclaim FAILED or expired concurrent record for retry
                winner.setRequestHash(requestHash);
                winner.setStatus(IdempotencyStatus.IN_PROGRESS);
                winner.setHttpStatus(null);
                winner.setResourcePublicId(null);
                winner.setResponseBody(null);
                winner.setExpiresAt(now.plus(ttl));
                repository.save(winner);
                return Optional.empty();
            });
        }
    }

    private <T> void markCompleted(String scope,
                                   String actorKey,
                                   String key,
                                   int httpStatus,
                                   String resourcePublicId,
                                   T body,
                                   Duration ttl) {
        requiresNewTx.executeWithoutResult(status -> {
            IdempotencyRecord record = repository.findByScopeAndActorKeyAndIdempotencyKey(scope, actorKey, key)
                    .orElseThrow(() -> new IllegalStateException("Missing idempotency record upon completion."));
            record.setStatus(IdempotencyStatus.COMPLETED);
            record.setHttpStatus(httpStatus);
            record.setResourcePublicId(resourcePublicId);
            record.setResponseBody(serializeBody(body));
            record.setExpiresAt(Instant.now().plus(ttl));
            repository.save(record);
        });
    }

    private void markFailed(String scope, String actorKey, String key) {
        try {
            requiresNewTx.executeWithoutResult(status ->
                    repository.findByScopeAndActorKeyAndIdempotencyKey(scope, actorKey, key)
                            .filter(r -> r.getStatus() == IdempotencyStatus.IN_PROGRESS)
                            .ifPresent(r -> {
                                r.setStatus(IdempotencyStatus.FAILED);
                                repository.save(r);
                            })
            );
        } catch (RuntimeException markEx) {
            log.warn("Failed to transition idempotency record to FAILED for scope={}, actor={}, key={}: {}",
                    scope, actorKey, key, markEx.getMessage());
        }
    }

    private static void assertSameHash(IdempotencyRecord existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException(
                    "Idempotency-Key was already used with different request parameters."
            );
        }
    }

    private <T> ExecutionOutcome<T> deserializeReplay(IdempotencyRecord record, Class<T> responseType) {
        try {
            T body = (record.getResponseBody() == null || responseType == Void.class)
                    ? null
                    : canonicalMapper.readValue(record.getResponseBody(), responseType);
            int status = record.getHttpStatus() != null ? record.getHttpStatus() : 200;
            return new ExecutionOutcome<>(true, status, record.getResourcePublicId(), body);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize replayed idempotency response.", ex);
        }
    }

    private String serializeBody(Object body) {
        if (body == null) {
            return null;
        }
        try {
            return canonicalMapper.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize idempotent response payload.", ex);
        }
    }

    private static String normalizeScope(String scope) {
        if (scope == null || scope.isBlank()) {
            throw new IllegalArgumentException("Idempotency scope cannot be blank.");
        }
        return scope.trim().toUpperCase();
    }
}

