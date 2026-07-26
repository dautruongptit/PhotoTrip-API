package com.travelalbum.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** Chỉ dùng để chẩn đoán local/dev (SEC-25) — KHÔNG lộ thông tin nhạy cảm ngoài path lưu ảnh. */
@Getter
@Builder
public class StorageDebugResponse {
    private String configuredRootPath;
    private String resolvedAbsolutePath;
    private boolean rootExists;
    private boolean rootWritable;
    private List<String> topLevelEntries;
}