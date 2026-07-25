package com.travelalbum.controller;

import com.travelalbum.common.ApiResponse;
import com.travelalbum.dto.response.AuditLogResponse;
import com.travelalbum.dto.response.DashboardResponse;
import com.travelalbum.dto.response.StatisticsResponse;
import com.travelalbum.dto.response.StorageOverviewResponse;
import com.travelalbum.dto.response.UserResponse;
import com.travelalbum.entity.AuditLog;
import com.travelalbum.repository.AuditLogRepository;
import com.travelalbum.repository.UserRepository;
import com.travelalbum.service.AdminService;
import com.travelalbum.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Toàn bộ endpoint Admin theo bảng API ở SEC-01. /logs có từ SEC-08; /dashboard,
 * /storage, /statistics, /users được bổ sung đầy đủ ở SEC-15.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final AdminService adminService;
    private final UserService userService;

    @GetMapping("/logs")
    public ApiResponse<Page<AuditLogResponse>> logs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long userId,
            Pageable pageable) {
        Page<AuditLog> logs = auditLogRepository.search(action, userId, pageable);
        Page<AuditLogResponse> response = logs.map(log -> AuditLogResponse.builder()
            .time(log.getCreatedAt())
            .userEmail(log.getUserId() != null
                ? userRepository.findById(log.getUserId()).map(u -> u.getEmail()).orElse("Unknown")
                : "Anonymous")
            .action(log.getAction())
            .ip(log.getIpAddress())
            .userAgent(log.getUserAgent())
            .result(log.getResult())
            .build());
        return ApiResponse.success("OK", response);
    }

    @GetMapping("/dashboard")
    public ApiResponse<DashboardResponse> dashboard() {
        return ApiResponse.success("OK", adminService.getDashboard());
    }

    @GetMapping("/storage")
    public ApiResponse<StorageOverviewResponse> storage() {
        return ApiResponse.success("OK", adminService.getStorageOverview());
    }

    @GetMapping("/statistics")
    public ApiResponse<StatisticsResponse> statistics() {
        return ApiResponse.success("OK", adminService.getStatistics());
    }

    /** Đồng nhất với GET /api/users (UserController) — tách route riêng theo đúng bảng API ở SEC-01. */
    @GetMapping("/users")
    public ApiResponse<Page<UserResponse>> users(Pageable pageable) {
        return ApiResponse.success("OK", userService.listAll(pageable));
    }
}
