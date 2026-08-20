package com.travelalbum.controller;

import com.travelalbum.common.ApiResponse;
import com.travelalbum.dto.response.LoginResponse;
import com.travelalbum.dto.response.UserResponse;
import com.travelalbum.entity.RefreshToken;
import com.travelalbum.entity.User;
import com.travelalbum.enums.UserStatus;
import com.travelalbum.exception.NotFoundException;
import com.travelalbum.repository.RefreshTokenRepository;
import com.travelalbum.repository.UserRepository;
import com.travelalbum.security.SessionCache;
import com.travelalbum.security.jwt.JwtTokenProvider;
import com.travelalbum.security.userdetails.UserPrincipal;
import com.travelalbum.service.AuditLogService;
import com.travelalbum.storage.ClientIpUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final SessionCache sessionCache;

    @Value("${app.cookie-secure:true}")
    private boolean cookieSecure;

    @PostMapping("/refresh")
    @Transactional
    public ApiResponse<LoginResponse> refresh(@CookieValue(value = "refresh_token", required = false) String rawToken,
                                              HttpServletRequest request,
                                              HttpServletResponse response) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new AccessDeniedException("No active session");
        }

        String hash = DigestUtils.sha256Hex(rawToken);
        String ip = ClientIpUtils.resolveIp(request);
        String userAgent = request.getHeader("User-Agent");

        RefreshToken rt = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> {
                    auditLogService.log(null, "REFRESH_TOKEN", "USER", null, ip, userAgent, "FAILED");
                    return new AccessDeniedException("Invalid refresh token");
                });

        if (rt.isRevoked()) {
            refreshTokenRepository.revokeAllByUserId(rt.getUserId());
            userRepository.findById(rt.getUserId()).ifPresent(u -> {
                u.setCurrentSessionId(null);
                userRepository.save(u);
                sessionCache.invalidate(u.getId());
            });
            auditLogService.log(rt.getUserId(), "REFRESH_TOKEN_REUSE_DETECTED", "USER",
                    rt.getUserId(), ip, userAgent, "FAILED");
            throw new AccessDeniedException("Refresh token reuse detected — all sessions revoked");
        }

        if (rt.getExpiresAt().isBefore(LocalDateTime.now())) {
            auditLogService.log(rt.getUserId(), "REFRESH_TOKEN", "USER", rt.getUserId(), ip, userAgent, "FAILED");
            throw new AccessDeniedException("Refresh token expired");
        }

        User user = userRepository.findById(rt.getUserId())
                .orElseThrow(() -> new NotFoundException("User not found"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            auditLogService.log(user.getId(), "REFRESH_TOKEN", "USER", user.getId(), ip, userAgent, "FAILED");
            throw new AccessDeniedException("User account is disabled");
        }

        if (rt.getSessionId() == null || !rt.getSessionId().equals(user.getCurrentSessionId())) {
            auditLogService.log(user.getId(), "REFRESH_TOKEN", "USER", user.getId(), ip, userAgent, "FAILED");
            throw new AccessDeniedException("Session replaced by a newer login");
        }

        rt.setRevoked(true);
        refreshTokenRepository.save(rt);

        String newRefreshToken = tokenProvider.generateRefreshToken();
        refreshTokenRepository.save(RefreshToken.builder()
                .userId(user.getId())
                .sessionId(rt.getSessionId())
                .tokenHash(DigestUtils.sha256Hex(newRefreshToken))
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build());

        ResponseCookie cookie = ResponseCookie.from("refresh_token", newRefreshToken)
                .httpOnly(true).secure(cookieSecure).sameSite("Strict").path("/api/auth")
                .maxAge(Duration.ofDays(7)).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        String newAccessToken = tokenProvider.generateAccessToken(user, rt.getSessionId());
        return ApiResponse.success("OK",
                LoginResponse.builder().accessToken(newAccessToken).user(UserResponse.from(user)).build());
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@CookieValue(value = "refresh_token", required = false) String rawToken,
                                    @AuthenticationPrincipal UserPrincipal principal,
                                    HttpServletRequest request) {
        if (rawToken != null) {
            String hash = DigestUtils.sha256Hex(rawToken);
            refreshTokenRepository.findByTokenHashAndRevokedFalse(hash)
                    .ifPresent(t -> { t.setRevoked(true); refreshTokenRepository.save(t); });
        }

        if (principal != null) {
            userRepository.findById(principal.getId()).ifPresent(user -> {
                user.setCurrentSessionId(null);
                userRepository.save(user);
                sessionCache.invalidate(user.getId());
            });
            auditLogService.log(principal.getId(), "LOGOUT", "USER", principal.getId(), null, null, "SUCCESS");
        }
        return ApiResponse.success("Logged out", null);
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@AuthenticationPrincipal UserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new NotFoundException("User not found"));
        return ApiResponse.success("OK", UserResponse.from(user));
    }
}