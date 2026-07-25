package com.travelalbum.service;

public interface AuditLogService {

    void log(Long userId, String action, String targetType, Object targetId,
              String ip, String userAgent, String result);
}
