package com.travelalbum.dto.response;

import lombok.Builder;
import lombok.Getter;

/** Số liệu tổng quan cho Dashboard Admin — xem SEC-01 (thiết kế field) / SEC-15 (implementation). */
@Getter
@Builder
public class DashboardResponse {
    private long totalUsers;
    private long totalEvents;
    private long totalPhotos;
    private long totalStorageUsed;      // bytes, tổng dung lượng đã dùng bởi toàn bộ user
    private long uploadsToday;
    private long diskUsableSpace;       // bytes, dung lượng đĩa vật lý còn trống
    private long diskTotalSpace;        // bytes, tổng dung lượng đĩa vật lý
}
