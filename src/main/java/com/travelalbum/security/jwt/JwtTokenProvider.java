package com.travelalbum.security.jwt;

import com.travelalbum.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * Sinh & xác thực JWT.
 * Claims: sub (userId), email, role, sid (session id — SEC-10), jti (token id), iss.
 * Cập nhật SEC-24: kiểm tra độ mạnh secret lúc khởi động, phân biệt EXPIRED/INVALID,
 * gắn jti + iss để dễ truy vết và chống nhầm lẫn issuer.
 */
@Component
public class JwtTokenProvider {

    private static final String ISSUER = "travel-photo-album";

    @Value("${jwt.secret:${JWT_SECRET}}")
    private String secret;

    @Value("${jwt.access-exp-ms:86400000}") // Default 1 ngày nếu không khai báo
    private long accessExpMs;

    @Value("${jwt.refresh-exp-ms:604800000}") // Default 7 ngày nếu không khai báo
    private long refreshExpMs;

    /** Trạng thái xác thực JWT — phân biệt EXPIRED (có thể tự refresh) và INVALID (phải logout). */
    public enum TokenStatus { VALID, EXPIRED, INVALID }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Chặn lỗi runtime WeakKeyException khó hiểu: HS256 yêu cầu key >= 256-bit (32 byte).
     * Nếu jwt.secret cấu hình sai/thiếu, app sẽ FAIL ngay khi khởi động thay vì lỗi lúc login.
     */
    @PostConstruct
    public void validateSecretStrength() {
        int byteLength = secret == null ? 0 : secret.getBytes(StandardCharsets.UTF_8).length;
        if (byteLength < 32) {
            throw new IllegalStateException(
                    "jwt.secret phải >= 32 byte (256-bit) cho thuật toán HS256. Độ dài hiện tại: " + byteLength
                            + " byte. Kiểm tra lại biến môi trường JWT_SECRET.");
        }
    }

    public String generateAccessToken(User user, String sessionId) {
        return Jwts.builder()
                .setId(UUID.randomUUID().toString())
                .setIssuer(ISSUER)
                .setSubject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .claim("sid", sessionId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + accessExpMs))
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();
    }

    /** Refresh Token là chuỗi ngẫu nhiên KHÔNG PHẢI JWT (opaque token) — chỉ lưu hash trong DB. */
    public String generateRefreshToken() {
        return UUID.randomUUID() + "" + UUID.randomUUID();
    }

    public long getRefreshExpMs() {
        return refreshExpMs;
    }

    public Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key())
                .requireIssuer(ISSUER)
                .build()
                .parseClaimsJws(token).getBody();
    }

    /** Giữ lại cho các nơi chỉ cần biết true/false (không phân biệt lý do). */
    public boolean isValid(String token) {
        return validateDetailed(token) == TokenStatus.VALID;
    }

    /**
     * Phân biệt EXPIRED và INVALID để tầng trên (JwtAuthFilter/EntryPoint) trả về đúng
     * errorCode cho Frontend: TOKEN_EXPIRED (nên tự gọi /api/auth/refresh) khác với
     * UNAUTHORIZED (token sai/giả mạo — phải bắt logout lại từ đầu).
     */
    public TokenStatus validateDetailed(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key()).requireIssuer(ISSUER).build().parseClaimsJws(token);
            return TokenStatus.VALID;
        } catch (ExpiredJwtException ex) {
            return TokenStatus.EXPIRED;
        } catch (JwtException | IllegalArgumentException ex) {
            return TokenStatus.INVALID;
        }
    }
}