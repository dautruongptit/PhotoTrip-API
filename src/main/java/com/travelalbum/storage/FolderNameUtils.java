package com.travelalbum.storage;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Sinh tên thư mục lưu trữ theo quy tắc SEC-26:
 *  - Thư mục cha (User):  {username}_{parentFolderCode}      vd: dautruong_000001
 *  - Thư mục con (Event): {yyyyMMdd}_{EventName}_{eventFolderCode}  vd: 20260727_DuLichHaGiang_000004
 *
 * parentFolderCode = users.id zero-pad 6 chữ số (tái dùng PK có sẵn, không thêm cột code riêng).
 * eventFolderCode  = events.id zero-pad 6 chữ số (tái dùng PK có sẵn, không thêm cột code riêng).
 * parentFolderId   = events.owner_id (đã là FK tới users.id từ trước — không cần thêm cột mới).
 */
public final class FolderNameUtils {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final Pattern NON_ALNUM_SPACE = Pattern.compile("[^a-zA-Z0-9\\s]");
    private static final Pattern NON_USERNAME_CHAR = Pattern.compile("[^a-zA-Z0-9_]");

    private FolderNameUtils() {
    }

    public static String zeroPad6(Long id) {
        return String.format("%06d", id);
    }

    public static String usernameFromEmail(String email) {
        String local = email != null && email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        String sanitized = NON_USERNAME_CHAR.matcher(local == null ? "user" : local).replaceAll("");
        return sanitized.isBlank() ? "user" : sanitized;
    }

    public static String buildUserFolderName(String email, Long userId) {
        return usernameFromEmail(email) + "_" + zeroPad6(userId);
    }

    public static String normalizeEventName(String rawName) {
        String noDiacritics = removeVietnameseDiacritics(rawName == null ? "" : rawName);
        String cleaned = NON_ALNUM_SPACE.matcher(noDiacritics).replaceAll(" ");
        String[] words = cleaned.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(w.charAt(0)));
            if (w.length() > 1) {
                sb.append(w.substring(1).toLowerCase());
            }
        }
        return sb.length() == 0 ? "Event" : sb.toString();
    }

    public static String removeVietnameseDiacritics(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        String stripped = DIACRITICS.matcher(normalized).replaceAll("");
        return stripped.replace('đ', 'd').replace('Đ', 'D');
    }

    public static String buildEventFolderName(java.time.LocalDate eventDate, String eventName, Long eventId) {
        String datePart = eventDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        return datePart + "_" + normalizeEventName(eventName) + "_" + zeroPad6(eventId);
    }
}
