package com.travelalbum.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Ghi log method + URL + tham số (query/body) + status + thời gian xử lý cho mọi request,
 * ra file (xem logging.file.name trong application.yml). Che các trường nhạy cảm
 * (password/token/secret) trước khi log — không bao giờ ghi giá trị thật ra file (SEC-02).
 */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    // Giới hạn số byte body được cache để đọc lại — tránh load nguyên file ảnh/video
    // lớn (multipart request cho phép tới 150MB) vào bộ nhớ chỉ để log (SEC-xx: DoS/OOM).
    private static final int MAX_CACHED_BODY_BYTES = 8 * 1024;

    // Che giá trị của bất kỳ field JSON nào có tên chứa password/token/secret,
    // không phân biệt hoa thường (vd password, newPassword, accessToken, refreshToken, dev-secret...).
    private static final Pattern SENSITIVE_FIELD_PATTERN = Pattern.compile(
            "(?i)(\"[^\"]*(password|token|secret)[^\"]*\"\\s*:\\s*)\"[^\"]*\"");

    private static final String MASK = "\"***\"";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ContentCachingRequestWrapper wrappedRequest =
                new ContentCachingRequestWrapper(request, MAX_CACHED_BODY_BYTES);

        long start = System.currentTimeMillis();
        try {
            chain.doFilter(wrappedRequest, response);
        } finally {
            long durationMs = System.currentTimeMillis() - start;
            logRequest(wrappedRequest, response.getStatus(), durationMs);
        }
    }

    private void logRequest(ContentCachingRequestWrapper request, int status, long durationMs) {
        String query = request.getQueryString();
        log.info("{} {}{} params={} status={} time={}ms",
                request.getMethod(),
                request.getRequestURI(),
                query != null ? "?" + maskQuery(query) : "",
                describeBody(request),
                status,
                durationMs);
    }

    private String describeBody(ContentCachingRequestWrapper request) {
        String contentType = request.getContentType();
        if (contentType != null && contentType.toLowerCase().startsWith("multipart/")) {
            return "[multipart, omitted]";
        }

        byte[] buf = request.getContentAsByteArray();
        if (buf.length == 0) {
            return "-";
        }

        String body = new String(buf, charsetOf(request));
        String masked = maskJson(body);
        return buf.length >= MAX_CACHED_BODY_BYTES ? masked + "...(truncated)" : masked;
    }

    private java.nio.charset.Charset charsetOf(HttpServletRequest request) {
        try {
            return request.getCharacterEncoding() != null
                    ? java.nio.charset.Charset.forName(request.getCharacterEncoding())
                    : StandardCharsets.UTF_8;
        } catch (java.nio.charset.UnsupportedCharsetException e) {
            return StandardCharsets.UTF_8;
        }
    }

    private String maskJson(String body) {
        return SENSITIVE_FIELD_PATTERN.matcher(body).replaceAll("$1" + MASK);
    }

    private String maskQuery(String query) {
        return query.replaceAll("(?i)((?:^|&)[^=&]*(password|token|secret)[^=&]*=)[^&]*", "$1" + MASK);
    }
}
