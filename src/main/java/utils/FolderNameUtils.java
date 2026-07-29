package utils;

public class FolderNameUtils {
    public static String zeroPad6(Long id) {
        return String.format("%06d", id);   // 1 -> "000001"
    }

    public static String usernameFromEmail(String email) {
        String local = email.substring(0, email.indexOf('@'));
        return local.replaceAll("[^a-zA-Z0-9_]", "");
    }

    public static String buildUserFolderName(String email, Long userId) {
        return usernameFromEmail(email) + "_" + zeroPad6(userId);
    }

    // "Du lịch Hà Giang" -> "DuLichHaGiang" (bỏ dấu, PascalCase, viết liền)
    public static String normalizeEventName(String rawName) {
        String noDiacritics = removeVietnameseDiacritics(rawName);
        String cleaned = noDiacritics.replaceAll("[^a-zA-Z0-9\\s]", " ");
        // ... viết hoa chữ đầu mỗi từ, nối liền
    }

    public static String buildEventFolderName(LocalDate eventDate, String eventName, Long eventId) {
        String datePart = eventDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return datePart + "_" + normalizeEventName(eventName) + "_" + zeroPad6(eventId);
    }

}
