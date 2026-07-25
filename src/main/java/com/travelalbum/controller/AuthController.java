package com.travelalbum.controller;

import com.travelalbum.common.ApiResponse;
import com.travelalbum.dto.response.UserResponse;
import com.travelalbum.entity.RefreshToken;
import com.travelalbum.entity.User;
import com.travelalbum.exception.NotFoundException;
import com.travelalbum.repository.RefreshTokenRepository;
import com.travelalbum.repository.UserRepository;
import com.travelalbum.security.SessionCache;
import com.travelalbum.security.jwt.JwtTokenProvider;
import com.travelalbum.security.userdetails.UserPrincipal;
import com.travelalbum.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final SessionCache sessionCache;

    @PostMapping("/refresh")
    public ApiResponse<Map<String, String>> refresh(@CookieValue("refresh_token") String rawToken) {
        String hash = DigestUtils.sha256Hex(rawToken);
        RefreshToken rt = refreshTokenRepository.findByTokenHashAndRevokedFalse(hash)
            .filter(t -> t.getExpiresAt().isAfter(LocalDateTime.now()))
            .orElseThrow(() -> new AccessDeniedException("Invalid or expired refresh token"));

        User user = userRepository.findById(rt.getUserId())
            .orElseThrow(() -> new NotFoundException("User not found"));

        // Chặn refresh nếu session của refresh token không còn là session hiện tại (SEC-10)
        if (rt.getSessionId() == null || !rt.getSessionId().equals(user.getCurrentSessionId())) {
            throw new AccessDeniedException("Session replaced by a newer login");
        }

        String newAccessToken = tokenProvider.generateAccessToken(user, rt.getSessionId());
        return ApiResponse.success("OK", Map.of("accessToken", newAccessToken));
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

        userRepository.findById(principal.getId()).ifPresent(user -> {
            user.setCurrentSessionId(null);
            userRepository.save(user);
            sessionCache.invalidate(user.getId());
        });

        auditLogService.log(principal.getId(), "LOGOUT", "USER", principal.getId(), null, null, "SUCCESS");
        return ApiResponse.success("Logged out", null);
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@AuthenticationPrincipal UserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
            .orElseThrow(() -> new NotFoundException("User not found"));
        return ApiResponse.success("OK", UserResponse.from(user));
    }
}
