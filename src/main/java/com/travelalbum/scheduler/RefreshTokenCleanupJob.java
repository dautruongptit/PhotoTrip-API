package com.travelalbum.scheduler;

import com.travelalbum.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Dọn định kỳ các refresh_token đã hết hạn quá lâu (revoked hoặc chưa) — bảng
 * refresh_tokens không tự xóa row khi revoke (chỉ set revoked=true, giữ lại phục vụ
 * phát hiện reuse — SEC-24), nên cần job này để tránh bảng phình vô hạn theo thời gian.
 *
 * Giữ lại 7 ngày sau expires_at trước khi xóa — đủ rộng rãi cho việc phát hiện reuse.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupJob.class);

    private static final int RETENTION_DAYS_AFTER_EXPIRY = 7;

    private final RefreshTokenRepository refreshTokenRepository;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS_AFTER_EXPIRY);
        int deleted = refreshTokenRepository.deleteExpiredBefore(cutoff);
        log.info("RefreshTokenCleanupJob: deleted {} expired refresh token(s) older than {}", deleted, cutoff);
    }
}
