package com.travelalbum.service;

import com.travelalbum.dto.response.DashboardResponse;
import com.travelalbum.dto.response.StatisticsResponse;
import com.travelalbum.dto.response.StorageOverviewResponse;

public interface AdminService {
    DashboardResponse getDashboard();
    StorageOverviewResponse getStorageOverview();
    StatisticsResponse getStatistics();
}
