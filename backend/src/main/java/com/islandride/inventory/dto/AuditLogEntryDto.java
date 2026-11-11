package com.islandride.inventory.dto;

import java.time.Instant;

public record AuditLogEntryDto(
        Long id,
        String action,
        String entityName,
        String entityId,
        Long actorUserId,
        String oldValues,
        String newValues,
        Instant createdAt
) {}

