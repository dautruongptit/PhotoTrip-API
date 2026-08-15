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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;

/**
 * Ghi log method + URL + tham số (query/body) + status + thời gian xử lý cho mọi request,
 * ra file (xem logging.file.name trong application.yml). Che các trường nhạy cảm
 * (password/token/secret, OAuth2 code/state...) trước khi log, và loại bỏ control char khỏi
 * mọi giá trị log để chống log injection — không bao giờ ghi giá trị thật ra file (SEC-02).
 * Logic mask/sanitize nằm ở RequestLogMasker (tách riêng để test không cần mock servlet).
 */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    // Giới hạn số byte body được cache để đọc lại — tránh load nguyên file ảnh/video
    // lớn (multipart request cho phép tới 150MB) vào bộ nhớ chỉ để log (SEC-xx: DoS/OOM).
    private static final int MAX_CACHED_BODY_BYTES = 8 * 1024;

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
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        String queryPart = query != null ? "?" + RequestLogMasker.maskQuery(uri, query) : "";

        log.info("{} {}{} params={} status={} time={}ms",
                RequestLogMasker.sanitizeForLog(request.getMethod()),
                RequestLogMasker.sanitizeForLog(uri),
                RequestLogMasker.sanitizeForLog(queryPart),
                RequestLogMasker.sanitizeForLog(describeBody(request)),
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

        boolean truncated = buf.length >= MAX_CACHED_BODY_BYTES;
        String body = new String(buf, charsetOf(request));
        String masked = contentType != null && contentType.toLowerCase().startsWith("application/x-www-form-urlencoded")
                ? RequestLogMasker.maskFormBody(body)
                : RequestLogMasker.maskJsonBody(body, truncated);
        return truncated ? masked + "...(truncated)" : masked;
    }

    private Charset charsetOf(HttpServletRequest request) {
        try {
            return request.getCharacterEncoding() != null
                    ? Charset.forName(request.getCharacterEncoding())
                    : StandardCharsets.UTF_8;
        } catch (UnsupportedCharsetException e) {
            return StandardCharsets.UTF_8;
        }
    }
}
