package com.travelalbum.security;

import java.util.regex.Pattern;

/**
 * Logic che (mask) dữ liệu nhạy cảm + chống log injection cho RequestLoggingFilter — tách
 * riêng thành class thuần (không phụ thuộc servlet) để test được trực tiếp, không cần mock
 * HttpServletRequest.
 *
 * Ghi chú các lỗ hổng đã fix so với bản mask-bằng-regex ban đầu:
 *  - Mask JSON chỉ khớp value dạng chuỗi có ngoặc kép đóng đầy đủ -> value dạng số/bool/null,
 *    hoặc body bị CẮT (truncate ở 8KB) làm field nhạy cảm mất dấu ngoặc kép đóng, đều lọt qua
 *    không bị mask (masking-parser-differential: regex không parse JSON thật, dễ bị bypass).
 *  - Body không phải JSON (vd application/x-www-form-urlencoded) trước đây không được mask
 *    field nào cả -> lộ y hệt query string (sensitive-data-in-logs).
 *  - Callback OAuth2 (/oauth2/**, /login/oauth2/code/**) nhận `code`/`state` qua query string —
 *    đây là bearer credential dùng 1 lần, nhưng tên field không chứa "token/password/secret"
 *    nên bị bỏ sót hoàn toàn khỏi mask cũ (sensitive-data-in-logs).
 *  - Giá trị log được nội suy trực tiếp từ dữ liệu người dùng gửi lên (URI/query/body) mà không
 *    loại bỏ CR/LF/control char -> log injection/log forging (CWE-117): attacker có thể chèn
 *    dòng log giả bằng cách nhét \r\n vào body.
 */
final class RequestLogMasker {

    private RequestLogMasker() {
    }

    static final String MASK = "***";
    private static final String JSON_MASK = "\"***\"";

    // Value JSON hợp lệ: chuỗi có đóng ngoặc kép, số, true/false/null.
    private static final Pattern SENSITIVE_JSON_FIELD = Pattern.compile(
            "(?i)(\"[^\"]*(password|token|secret)[^\"]*\"\\s*:\\s*)(\"[^\"]*\"|-?\\d+(?:\\.\\d+)?|true|false|null)");

    // Field nhạy cảm bị cắt cụt do body bị truncate (không có dấu ngoặc kép đóng) — mask phần
    // còn lại tới hết chuỗi thay vì bỏ qua.
    private static final Pattern SENSITIVE_JSON_FIELD_DANGLING = Pattern.compile(
            "(?i)(\"[^\"]*(password|token|secret)[^\"]*\"\\s*:\\s*\")[^\"]*$");

    private static final Pattern SENSITIVE_FORM_FIELD = Pattern.compile(
            "(?i)((?:^|&)[^=&]*(password|token|secret)[^=&]*=)[^&]*");

    private static final Pattern OAUTH2_CALLBACK_PATH = Pattern.compile("^/(oauth2/|login/oauth2/).*");

    // CR/LF và các control char khác — loại bỏ để 1 request không thể chèn dòng log giả.
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x1F\\x7F]");

    /** Che field nhạy cảm trong JSON body — bao gồm cả trường hợp value không phải chuỗi và body bị truncate. */
    static String maskJsonBody(String body, boolean truncated) {
        String masked = SENSITIVE_JSON_FIELD.matcher(body).replaceAll(m -> {
            String value = m.group(3);
            return m.group(1) + (value.startsWith("\"") ? JSON_MASK : MASK);
        });
        if (truncated) {
            masked = SENSITIVE_JSON_FIELD_DANGLING.matcher(masked).replaceAll("$1" + MASK);
        }
        return masked;
    }

    /** Che field nhạy cảm trong body dạng application/x-www-form-urlencoded (giống query string). */
    static String maskFormBody(String body) {
        return SENSITIVE_FORM_FIELD.matcher(body).replaceAll("$1" + MASK);
    }

    /**
     * Che query string. Với callback OAuth2 (code/state là bearer credential dùng 1 lần,
     * không có tên field cố định) thì che TOÀN BỘ query string thay vì lọc theo tên field.
     */
    static String maskQuery(String requestUri, String query) {
        if (OAUTH2_CALLBACK_PATH.matcher(requestUri).matches()) {
            return "[oauth2 callback, omitted]";
        }
        return SENSITIVE_FORM_FIELD.matcher(query).replaceAll("$1" + MASK);
    }

    /** Loại bỏ CR/LF/control char khỏi giá trị trước khi ghi log — chống log injection (CWE-117). */
    static String sanitizeForLog(String value) {
        return value == null ? null : CONTROL_CHARS.matcher(value).replaceAll(" ");
    }
}
