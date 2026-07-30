package com.travelalbum.security.jwt;

import com.travelalbum.entity.User;
import com.travelalbum.repository.UserRepository;
import com.travelalbum.security.SessionCache;
import com.travelalbum.security.userdetails.UserPrincipal;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String ATTR_TOKEN_STATUS = "jwt_token_status";

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final SessionCache sessionCache;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            JwtTokenProvider.TokenStatus status = tokenProvider.validateDetailed(token);
            request.setAttribute(ATTR_TOKEN_STATUS, status);

            if (status == JwtTokenProvider.TokenStatus.VALID) {
                Claims claims = tokenProvider.parseClaims(token);
                Long userId = Long.valueOf(claims.getSubject());
                String tokenSid = claims.get("sid", String.class);

                String currentSid = sessionCache.getOrLoad(userId, () ->
                        userRepository.findById(userId).map(User::getCurrentSessionId).orElse(null));

                if (tokenSid != null && tokenSid.equals(currentSid)) {
                    userRepository.findById(userId).ifPresent(user -> {
                        UserPrincipal principal = UserPrincipal.from(user);
                        var auth = new UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities());
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    });
                } else {
                    // sid không khớp -> phiên đã bị thay thế bởi lần đăng nhập mới ở nơi khác (SEC-10)
                    request.setAttribute(ATTR_TOKEN_STATUS, JwtTokenProvider.TokenStatus.INVALID);
                }
            }
        }
        chain.doFilter(request, response);
    }
}