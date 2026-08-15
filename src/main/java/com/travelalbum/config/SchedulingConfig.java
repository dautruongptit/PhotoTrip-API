package com.travelalbum.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Bật @Scheduled cho các job định kỳ (vd RefreshTokenCleanupJob). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
