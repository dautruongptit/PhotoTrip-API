package com.travelalbum.storage;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class FolderNameUtilsTest {

    @Test
    void zeroPad6_padsToSixDigits() {
        assertThat(FolderNameUtils.zeroPad6(1L)).isEqualTo("000001");
        assertThat(FolderNameUtils.zeroPad6(42L)).isEqualTo("000042");
        assertThat(FolderNameUtils.zeroPad6(123456L)).isEqualTo("123456");
    }

    @Test
    void usernameFromEmail_extractsLocalPartAndStripsSpecialChars() {
        assertThat(FolderNameUtils.usernameFromEmail("dautruong@gmail.com")).isEqualTo("dautruong");
        assertThat(FolderNameUtils.usernameFromEmail("da.truong+test@gmail.com")).isEqualTo("datruongtest");
    }

    @Test
    void usernameFromEmail_fallsBackToUser_whenBlankOrNull() {
        assertThat(FolderNameUtils.usernameFromEmail(null)).isEqualTo("user");
        assertThat(FolderNameUtils.usernameFromEmail("@@@")).isEqualTo("user");
    }

    @Test
    void buildUserFolderName_matchesSec26Convention() {
        // vd: dautruong_000001
        assertThat(FolderNameUtils.buildUserFolderName("dautruong@gmail.com", 1L))
                .isEqualTo("dautruong_000001");
    }

    @Test
    void normalizeEventName_removesDiacriticsAndPascalCases() {
        assertThat(FolderNameUtils.normalizeEventName("du lịch hà giang")).isEqualTo("DuLichHaGiang");
        assertThat(FolderNameUtils.normalizeEventName("Đà Lạt 2026")).isEqualTo("DaLat2026");
    }

    @Test
    void normalizeEventName_fallsBackToEvent_whenBlank() {
        assertThat(FolderNameUtils.normalizeEventName("")).isEqualTo("Event");
        assertThat(FolderNameUtils.normalizeEventName(null)).isEqualTo("Event");
    }

    @Test
    void removeVietnameseDiacritics_handlesDStroke() {
        assertThat(FolderNameUtils.removeVietnameseDiacritics("Đà Nẵng")).isEqualTo("Da Nang");
    }

    @Test
    void buildEventFolderName_matchesSec26Convention() {
        // vd: 20260727_DuLichHaGiang_000004
        LocalDate date = LocalDate.of(2026, 7, 27);
        assertThat(FolderNameUtils.buildEventFolderName(date, "du lịch hà giang", 4L))
                .isEqualTo("20260727_DuLichHaGiang_000004");
    }
}
