package com.travelalbum.security.oauth2;

import com.travelalbum.entity.RefreshToken;
import com.travelalbum.entity.User;
import com.travelalbum.repository.RefreshTokenRepository;
import com.travelalbum.repository.UserRepository;
import com.travelalbum.security.SessionCache;
import com.travelalbum.security.jwt.JwtTokenProvider;
import com.travelalbum.service.AuditLogService;
import com.travelalbum.storage.ClientIpUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Sinh session mới + JWT sau khi login Google thành công.
 * Ghi đè current_session_id -> vô hiệu hoá phiên cũ gần như tức thời (SEC-10).
 * Ghi Audit Log LOGIN kèm thời gian + IP (SEC-08).
 */
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditLogService auditLogService;
    private final SessionCache sessionCache;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String googleId = oAuth2User.getAttribute("sub");
        User user = userRepository.findByGoogleId(googleId)
            .orElseThrow(() -> new IllegalStateException("User not found after OAuth2 login"));

        String ip = ClientIpUtils.resolveIp(request);
        String userAgent = request.getHeader("User-Agent");

        // 1. Sinh session mới, ghi đè session cũ -> phiên cũ mất hiệu lực ngay (SEC-10)
        String newSessionId = UUID.randomUUID().toString();
        user.setCurrentSessionId(newSessionId);
        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(ip);
        userRepository.save(user);
        sessionCache.put(user.getId(), newSessionId);

        // 2. Revoke toàn bộ refresh token cũ của user
        refreshTokenRepository.revokeAllByUserId(user.getId());

        // 3. Sinh Access Token + Refresh Token mới, đính kèm sid
        String accessToken = tokenProvider.generateAccessToken(user, newSessionId);
        String refreshToken = tokenProvider.generateRefreshToken();
        refreshTokenRepository.save(RefreshToken.builder()
            .userId(user.getId())
            .sessionId(newSessionId)
            .tokenHash(DigestUtils.sha256Hex(refreshToken))
            .expiresAt(LocalDateTime.now().plusDays(7))
            .revoked(false)
            .build());

        auditLogService.log(user.getId(), "LOGIN", "USER", user.getId(), ip, userAgent, "SUCCESS");

        ResponseCookie cookie = ResponseCookie.from("refresh_token", refreshToken)
            .httpOnly(true).secure(true).sameSite("Strict").path("/api/auth")
            .maxAge(Duration.ofDays(7)).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        response.sendRedirect(frontendUrl + "/oauth2/callback?token=" + accessToken);
    }
}
