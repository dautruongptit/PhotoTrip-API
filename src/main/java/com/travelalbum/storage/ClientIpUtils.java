package com.travelalbum.storage;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/** Lấy đúng IP thật của client khi Backend chạy sau Nginx reverse proxy — xem SEC-06/SEC-08. */
public final class ClientIpUtils {

    private ClientIpUtils() {
    }

    public static String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
