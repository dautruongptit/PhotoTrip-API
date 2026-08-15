package com.travelalbum.scheduler;

import com.travelalbum.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenCleanupJobTest {

    @Mock private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenCleanupJob cleanupJob;

    @Test
    void cleanupExpiredTokens_deletesTokensOlderThan7DaysPastExpiry() {
        when(refreshTokenRepository.deleteExpiredBefore(any())).thenReturn(3);

        cleanupJob.cleanupExpiredTokens();

        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(refreshTokenRepository, times(1)).deleteExpiredBefore(cutoffCaptor.capture());

        LocalDateTime expectedCutoff = LocalDateTime.now().minusDays(7);
        Duration diff = Duration.between(cutoffCaptor.getValue(), expectedCutoff).abs();
        assertThat(diff).isLessThan(Duration.ofSeconds(5));
    }
}
