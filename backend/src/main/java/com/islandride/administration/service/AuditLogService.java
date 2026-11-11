package com.islandride.administration.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.islandride.administration.domain.AuditLog;
import com.islandride.administration.repository.AuditLogRepository;
import com.islandride.core.filter.CorrelationIdFilter;
import com.islandride.identityaccess.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public AuditLogService(AuditLogRepository auditLogRepository, UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(Long actorUserId,
                       String action,
                       String entityName,
                       String entityId,
                       Map<String, Object> oldValues,
                       Map<String, Object> newValues) {
        AuditLog entry = new AuditLog();
        if (actorUserId != null) {
            userRepository.findById(actorUserId).ifPresent(entry::setActorUser);
        }
        entry.setAction(action);
        entry.setEntityName(entityName);
        entry.setEntityId(entityId);
        entry.setCorrelationId(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY));

        HttpServletRequest req = currentRequest();
        if (req != null) {
            entry.setIpAddress(extractIp(req));
            String ua = req.getHeader("User-Agent");
            entry.setUserAgent(ua != null && ua.length() > 255 ? ua.substring(0, 255) : ua);
        }

        entry.setOldValues(toJson(oldValues));
        entry.setNewValues(toJson(newValues));
        auditLogRepository.save(entry);
    }

    private String toJson(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize audit log JSON payload: {}", ex.getMessage());
            return null;
        }
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    private String extractIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            return first.length() > 45 ? first.substring(0, 45) : first;
        }
        String addr = request.getRemoteAddr();
        return addr != null && addr.length() > 45 ? addr.substring(0, 45) : addr;
    }
}

