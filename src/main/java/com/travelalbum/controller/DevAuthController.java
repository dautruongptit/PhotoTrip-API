package com.travelalbum.controller;

import com.travelalbum.common.ApiResponse;
import com.travelalbum.dto.request.DevLoginRequest;
import com.travelalbum.dto.response.LoginResponse;
import com.travelalbum.dto.response.UserResponse;
import com.travelalbum.entity.RefreshToken;
import com.travelalbum.entity.User;
import com.travelalbum.enums.Role;
import com.travelalbum.enums.UserStatus;
import com.travelalbum.repository.RefreshTokenRepository;
import com.travelalbum.repository.UserRepository;
import com.travelalbum.security.SessionCache;
import com.travelalbum.security.jwt.JwtTokenProvider;
import com.travelalbum.service.AuditLogService;
import com.travelalbum.storage.ClientIpUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * SEC-25 — Endpoint đăng nhập tắt (bypass Google) CHỈ để test bằng Postman ở môi trường
 * local/dev. Tự tạo/tìm User theo email, phát hành session + JWT + refresh token giống
 * hệt luồng OAuth2SuccessHandler (SEC-10/SEC-24), trả JSON trực tiếp — không cần trình duyệt.
 *
 * AN TOÀN:
 *  - Chỉ tồn tại khi app.dev-login-enabled=true (mặc định FALSE — @ConditionalOnProperty
 *    khiến bean này KHÔNG được tạo nếu tắt, endpoint biến mất hoàn toàn, không chỉ ẩn).
 *  - Yêu cầu thêm header X-Dev-Secret khớp app.dev-login-secret — phòng trường hợp lỡ tay
 *    bật flag ở môi trường sai, vẫn còn 1 lớp chặn nữa.
 *  - TUYỆT ĐỐI không set DEV_LOGIN_ENABLED=true ở Production.
 */
@RestController
@RequestMapping("/api/auth/dev")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.dev-login-enabled", havingValue = "true")
public class DevAuthController {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider tokenProvider;
    private final AuditLogService auditLogService;
    private final SessionCache sessionCache;

    @Value("${app.dev-login-secret:}")
    private String devLoginSecret;

    @Value("${app.cookie-secure:true}")
    private boolean cookieSecure;

    @PostMapping("/login")
    @Transactional
    public ApiResponse<LoginResponse> devLogin(@Valid @RequestBody DevLoginRequest req,
                                               @RequestHeader(value = "X-Dev-Secret", required = false) String providedSecret,
                                               HttpServletRequest request,
                                               HttpServletResponse response) {
        if (devLoginSecret == null || devLoginSecret.isBlank() || !devLoginSecret.equals(providedSecret)) {
            throw new AccessDeniedException("Invalid or missing X-Dev-Secret header");
        }

        String ip = ClientIpUtils.resolveIp(request);
        String userAgent = request.getHeader("User-Agent");

        User user = userRepository.findByEmail(req.getEmail())
                .orElseGet(() -> User.builder()
                        .email(req.getEmail())
                        .fullName(req.getFullName() != null ? req.getFullName() : "Dev Tester")
                        .googleId("DEV_" + UUID.randomUUID())   // giả googleId — user này không thật sự qua Google
                        .emailVerified(true)
                        .role(Role.USER)
                        .status(UserStatus.ACTIVE)
                        .storageUsed(0L)
                        .storageQuota(5L * 1024 * 1024 * 1024)
                        .build());
        userRepository.save(user);

        // ---- Sinh session mới, y hệt OAuth2SuccessHandler (SEC-10) ----
        String newSessionId = UUID.randomUUID().toString();
        user.setCurrentSessionId(newSessionId);
        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(ip);
        userRepository.save(user);
        sessionCache.put(user.getId(), newSessionId);

        refreshTokenRepository.revokeAllByUserId(user.getId());

        String accessToken = tokenProvider.generateAccessToken(user, newSessionId);
        String refreshToken = tokenProvider.generateRefreshToken();
        refreshTokenRepository.save(RefreshToken.builder()
                .userId(user.getId())
                .sessionId(newSessionId)
                .tokenHash(DigestUtils.sha256Hex(refreshToken))
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build());

        auditLogService.log(user.getId(), "DEV_LOGIN", "USER", user.getId(), ip, userAgent, "SUCCESS");

        ResponseCookie cookie = ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true).secure(cookieSecure).sameSite("Strict").path("/api/auth")
                .maxAge(Duration.ofDays(7)).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ApiResponse.success("Dev login OK — KHÔNG dùng ở Production",
                LoginResponse.builder().accessToken(accessToken).user(UserResponse.from(user)).build());
    }
}