package com.travelalbum.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelalbum.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class JwtAuthEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        Object status = request.getAttribute(JwtAuthFilter.ATTR_TOKEN_STATUS);
        boolean expired = status == JwtTokenProvider.TokenStatus.EXPIRED;

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(objectMapper.writeValueAsString(
                expired
                        ? ApiResponse.error("Access token expired", "TOKEN_EXPIRED")
                        : ApiResponse.error("Unauthorized", "UNAUTHORIZED")));
    }
}