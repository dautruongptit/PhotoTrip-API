package com.travelalbum.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLogMaskerTest {

    @Test
    void maskJsonBody_masksStringSensitiveValue() {
        String body = "{\"email\":\"a@b.com\",\"password\":\"hunter2\"}";

        String masked = RequestLogMasker.maskJsonBody(body, false);

        assertThat(masked).contains("\"password\":\"***\"");
        assertThat(masked).doesNotContain("hunter2");
        assertThat(masked).contains("a@b.com");
    }

    @Test
    void maskJsonBody_masksNonStringSensitiveValue() {
        // Value không phải chuỗi (số/bool/null) trước đây lọt qua vì regex cũ chỉ khớp "..."
        String body = "{\"secretCode\":123456}";

        String masked = RequestLogMasker.maskJsonBody(body, false);

        assertThat(masked).doesNotContain("123456");
        assertThat(masked).contains("\"secretCode\":***");
    }

    @Test
    void maskJsonBody_masksDanglingFieldWhenBodyTruncated() {
        // Body bị cắt ở giữa value của field nhạy cảm (mất dấu " đóng) -> vẫn phải mask phần còn lại
        String truncatedBody = "{\"email\":\"a@b.com\",\"password\":\"hunter2andmore";

        String masked = RequestLogMasker.maskJsonBody(truncatedBody, true);

        assertThat(masked).doesNotContain("hunter2");
    }

    @Test
    void maskJsonBody_doesNotMaskDanglingField_whenNotTruncated() {
        // Không truncate thì không áp dụng rule "dangling" (tránh mask nhầm JSON hợp lệ khác)
        String body = "{\"password\":\"hunter2\"}";

        String masked = RequestLogMasker.maskJsonBody(body, false);

        assertThat(masked).isEqualTo("{\"password\":\"***\"}");
    }

    @Test
    void maskFormBody_masksFormUrlEncodedSensitiveField() {
        // Body dạng application/x-www-form-urlencoded trước đây không được mask field nào cả
        String body = "email=a%40b.com&password=hunter2";

        String masked = RequestLogMasker.maskFormBody(body);

        assertThat(masked).doesNotContain("hunter2");
        assertThat(masked).contains("password=***");
        assertThat(masked).contains("email=a%40b.com");
    }

    @Test
    void maskQuery_masksSensitiveParamOnRegularPath() {
        String masked = RequestLogMasker.maskQuery("/api/auth/refresh", "foo=bar&token=abc123");

        assertThat(masked).doesNotContain("abc123");
        assertThat(masked).contains("foo=bar");
    }

    @Test
    void maskQuery_omitsEntireQuery_onOAuth2CallbackPath() {
        // /login/oauth2/code/** nhận `code`/`state` (bearer credential dùng 1 lần) qua query
        // string — tên field không chứa password/token/secret nên phải xử lý riêng, che toàn bộ.
        String masked = RequestLogMasker.maskQuery(
                "/login/oauth2/code/google", "code=4/0AY0e-g7abc&state=xyz&scope=email");

        assertThat(masked).doesNotContain("4/0AY0e-g7abc");
        assertThat(masked).doesNotContain("xyz");
        assertThat(masked).isEqualTo("[oauth2 callback, omitted]");
    }

    @Test
    void maskQuery_omitsEntireQuery_onOAuth2AuthorizePath() {
        String masked = RequestLogMasker.maskQuery("/oauth2/authorization/google", "redirect_uri=/dashboard");

        assertThat(masked).isEqualTo("[oauth2 callback, omitted]");
    }

    @Test
    void sanitizeForLog_stripsNewlinesToPreventLogForging() {
        // Không có sanitize thì attacker chèn \r\n vào body/URI có thể giả mạo 1 dòng log khác
        String malicious = "Event\r\n2026-08-15 00:00:00 INFO fake log line status=200";

        String sanitized = RequestLogMasker.sanitizeForLog(malicious);

        assertThat(sanitized).doesNotContain("\r");
        assertThat(sanitized).doesNotContain("\n");
    }

    @Test
    void sanitizeForLog_leavesNormalTextUnchanged() {
        String normal = "/api/events?keyword=Đà Lạt";

        assertThat(RequestLogMasker.sanitizeForLog(normal)).isEqualTo(normal);
    }
}
