package com.islandride.administration.repository;

import com.islandride.administration.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByEntityNameAndEntityIdOrderByCreatedAtDesc(String entityName, String entityId);

    List<AuditLog> findByActorUserIdOrderByCreatedAtDesc(Long actorUserId);

    List<AuditLog> findByCorrelationId(String correlationId);
}

