package com.travelalbum.repository;

import com.travelalbum.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByActionAndCreatedAtBetween(
        String action, LocalDateTime from, LocalDateTime to, Pageable pageable);

    Page<AuditLog> findByUserId(Long userId, Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE "
        + "(:action IS NULL OR a.action = :action) AND "
        + "(:userId IS NULL OR a.userId = :userId) "
        + "ORDER BY a.createdAt DESC")
    Page<AuditLog> search(@Param("action") String action, @Param("userId") Long userId, Pageable pageable);
}
