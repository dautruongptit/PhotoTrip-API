package com.travelalbum.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Bật @Async cho AuditLogServiceImpl.log() — xem SEC-08. */
@Configuration
@EnableAsync
public class AsyncConfig {
}
