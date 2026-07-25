package com.travelalbum.storage;

/**
 * Kết quả trả về sau khi StorageService lưu 1 file — xem SEC-13.
 */
public record StoredFile(
    String fileName,
    String relativePath,
    String checksum,
    Integer width,
    Integer height
) {}
