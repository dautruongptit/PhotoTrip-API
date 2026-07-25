package com.travelalbum.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** GET /api/admin/statistics — xem SEC-01/SEC-15. */
@Getter
@Builder
public class StatisticsResponse {
    private long uploadsToday;
    private List<MonthlyUploadCount> uploadsByMonth;
    private List<UserStorageItem> topUsersByStorage;
}
