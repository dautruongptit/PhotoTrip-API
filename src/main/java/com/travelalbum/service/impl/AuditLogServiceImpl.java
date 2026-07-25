package com.travelalbum.service.impl;

import com.travelalbum.entity.AuditLog;
import com.travelalbum.repository.AuditLogRepository;
import com.travelalbum.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** Ghi log bất đồng bộ, không chặn response chính — xem SEC-08. */
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Override
    @Async
    public void log(Long userId, String action, String targetType, Object targetId,
                     String ip, String userAgent, String result) {
        AuditLog entry = AuditLog.builder()
            .userId(userId)
            .action(action)
            .targetType(targetType)
            .targetId(targetId != null ? Long.valueOf(targetId.toString()) : null)
            .ipAddress(ip)
            .userAgent(truncate(userAgent, 255))
            .result(result)
            .createdAt(LocalDateTime.now())
            .build();
        auditLogRepository.save(entry);
    }

    private String truncate(String s, int max) {
        return s == null ? null : (s.length() > max ? s.substring(0, max) : s);
    }
}
