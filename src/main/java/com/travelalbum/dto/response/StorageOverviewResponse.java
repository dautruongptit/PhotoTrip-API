package com.travelalbum.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** Chi tiết dung lượng lưu trữ — GET /api/admin/storage (SEC-01/SEC-15). */
@Getter
@Builder
public class StorageOverviewResponse {
    private long totalStorageUsed;      // tổng bytes đã dùng (theo users.storage_used)
    private long totalStorageQuota;     // tổng bytes quota đã cấp cho toàn bộ user
    private long diskTotalSpace;        // tổng dung lượng đĩa vật lý
    private long diskUsableSpace;       // dung lượng đĩa còn trống thực tế
    private double diskUsagePercent;    // % đã dùng so với disk total, cảnh báo khi > 80-90%
    private List<UserStorageItem> topUsersByStorage;
}
