package com.travelalbum.security.jwt;

import com.travelalbum.entity.User;
import com.travelalbum.enums.Role;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String VALID_SECRET = "01234567890123456789012345678901"; // 32 byte ASCII, đủ cho HS256

    private JwtTokenProvider provider;
    private User user;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "secret", VALID_SECRET);
        ReflectionTestUtils.setField(provider, "accessExpMs", 30 * 60 * 1000L);
        ReflectionTestUtils.setField(provider, "refreshExpMs", 7 * 24 * 60 * 60 * 1000L);

        user = User.builder()
                .id(1L)
                .email("dautruong@gmail.com")
                .role(Role.USER)
                .build();
    }

    @Test
    void validateSecretStrength_throws_whenSecretTooShort() {
        JwtTokenProvider weak = new JwtTokenProvider();
        ReflectionTestUtils.setField(weak, "secret", "too-short");

        assertThatThrownBy(weak::validateSecretStrength)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256-bit");
    }

    @Test
    void validateSecretStrength_passes_whenSecretIsLongEnough() {
        // Không ném exception là đủ để pass
        provider.validateSecretStrength();
    }

    @Test
    void generateAccessToken_embedsExpectedClaims() {
        String token = provider.generateAccessToken(user, "session-123");

        Claims claims = provider.parseClaims(token);
        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.get("email", String.class)).isEqualTo("dautruong@gmail.com");
        assertThat(claims.get("role", String.class)).isEqualTo("USER");
        assertThat(claims.get("sid", String.class)).isEqualTo("session-123");
        assertThat(claims.getId()).isNotBlank(); // jti
        assertThat(claims.getIssuer()).isEqualTo("travel-photo-album");
    }

    @Test
    void isValid_returnsTrue_forFreshlyGeneratedToken() {
        String token = provider.generateAccessToken(user, "session-123");
        assertThat(provider.isValid(token)).isTrue();
        assertThat(provider.validateDetailed(token)).isEqualTo(JwtTokenProvider.TokenStatus.VALID);
    }

    @Test
    void validateDetailed_returnsInvalid_forGarbageToken() {
        assertThat(provider.validateDetailed("not-a-real-jwt")).isEqualTo(JwtTokenProvider.TokenStatus.INVALID);
        assertThat(provider.isValid("not-a-real-jwt")).isFalse();
    }

    @Test
    void validateDetailed_returnsExpired_forExpiredToken() {
        ReflectionTestUtils.setField(provider, "accessExpMs", -1000L); // hết hạn ngay khi vừa sinh
        String expiredToken = provider.generateAccessToken(user, "session-123");

        assertThat(provider.validateDetailed(expiredToken)).isEqualTo(JwtTokenProvider.TokenStatus.EXPIRED);
        assertThat(provider.isValid(expiredToken)).isFalse();
    }

    @Test
    void generateRefreshToken_isRandomAndOpaque() {
        String t1 = provider.generateRefreshToken();
        String t2 = provider.generateRefreshToken();

        assertThat(t1).isNotBlank();
        assertThat(t2).isNotBlank();
        assertThat(t1).isNotEqualTo(t2);
        // Refresh token KHÔNG phải JWT — không có dấu chấm phân tách header.payload.signature
        assertThat(t1).doesNotContain(".");
    }

    @Test
    void getRefreshExpMs_returnsConfiguredValue() {
        assertThat(provider.getRefreshExpMs()).isEqualTo(7 * 24 * 60 * 60 * 1000L);
    }
}
