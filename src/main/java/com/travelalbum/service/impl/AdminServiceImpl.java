package com.travelalbum.service.impl;

import com.travelalbum.dto.response.DashboardResponse;
import com.travelalbum.dto.response.MonthlyUploadCount;
import com.travelalbum.dto.response.StatisticsResponse;
import com.travelalbum.dto.response.StorageOverviewResponse;
import com.travelalbum.dto.response.UserStorageItem;
import com.travelalbum.entity.User;
import com.travelalbum.repository.EventRepository;
import com.travelalbum.repository.PhotoRepository;
import com.travelalbum.repository.UserRepository;
import com.travelalbum.service.AdminService;
import com.travelalbum.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Tổng hợp số liệu cho Dashboard/Storage/Statistics Admin — thiết kế field ở SEC-01,
 * implementation đầy đủ ở SEC-15.
 */
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final PhotoRepository photoRepository;
    private final StorageService storageService;

    @Override
    public DashboardResponse getDashboard() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        return DashboardResponse.builder()
            .totalUsers(userRepository.count())
            .totalEvents(eventRepository.count())
            .totalPhotos(photoRepository.count())
            .totalStorageUsed(userRepository.sumStorageUsed())
            .uploadsToday(photoRepository.countByUploadedTimeBetween(startOfDay, endOfDay))
            .diskUsableSpace(storageService.getDiskUsableSpace())
            .diskTotalSpace(storageService.getDiskTotalSpace())
            .build();
    }

    @Override
    public StorageOverviewResponse getStorageOverview() {
        long diskTotal = storageService.getDiskTotalSpace();
        long diskUsable = storageService.getDiskUsableSpace();
        long diskUsed = diskTotal - diskUsable;
        double usagePercent = diskTotal > 0 ? (diskUsed * 100.0 / diskTotal) : 0.0;

        List<UserStorageItem> topUsers = userRepository.findTop5ByOrderByStorageUsedDesc()
            .stream()
            .map(this::toStorageItem)
            .toList();

        return StorageOverviewResponse.builder()
            .totalStorageUsed(userRepository.sumStorageUsed())
            .totalStorageQuota(userRepository.sumStorageQuota())
            .diskTotalSpace(diskTotal)
            .diskUsableSpace(diskUsable)
            .diskUsagePercent(Math.round(usagePercent * 100.0) / 100.0)
            .topUsersByStorage(topUsers)
            .build();
    }

    @Override
    public StatisticsResponse getStatistics() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        LocalDateTime sixMonthsAgo = LocalDate.now().minusMonths(6).withDayOfMonth(1).atStartOfDay();

        List<MonthlyUploadCount> uploadsByMonth = photoRepository
            .countUploadsGroupedByMonth(sixMonthsAgo)
            .stream()
            .map(this::toMonthlyUploadCount)
            .toList();

        List<UserStorageItem> topUsers = userRepository.findTop5ByOrderByStorageUsedDesc()
            .stream()
            .map(this::toStorageItem)
            .toList();

        return StatisticsResponse.builder()
            .uploadsToday(photoRepository.countByUploadedTimeBetween(startOfDay, endOfDay))
            .uploadsByMonth(uploadsByMonth)
            .topUsersByStorage(topUsers)
            .build();
    }

    private UserStorageItem toStorageItem(User user) {
        return UserStorageItem.builder()
            .userId(user.getId())
            .email(user.getEmail())
            .storageUsed(user.getStorageUsed())
            .storageQuota(user.getStorageQuota())
            .build();
    }

    /** row[0] = Timestamp (date_trunc('month', ...)), row[1] = Number (COUNT) — xem PhotoRepository. */
    private MonthlyUploadCount toMonthlyUploadCount(Object[] row) {
        Timestamp ts = (Timestamp) row[0];
        long count = ((Number) row[1]).longValue();
        String month = YearMonth.from(ts.toLocalDateTime()).format(MONTH_FORMAT);
        return new MonthlyUploadCount(month, count);
    }
}
