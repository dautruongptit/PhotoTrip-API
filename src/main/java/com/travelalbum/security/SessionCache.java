package com.travelalbum.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Cache session_id hiện tại của user để JwtAuthFilter không phải query DB
 * mỗi request — xem SEC-10. TTL 30s là độ trễ tối đa để vô hiệu hoá phiên cũ.
 * Khi scale nhiều instance Backend, thay bằng Redis dùng chung.
 */
@Component
public class SessionCache {

    private final Cache<Long, String> cache = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofSeconds(30))
        .maximumSize(100_000)
        .build();

    public String getOrLoad(Long userId, Supplier<String> loader) {
        return cache.get(userId, id -> loader.get());
    }

    public void put(Long userId, String sid) {
        cache.put(userId, sid);
    }

    public void invalidate(Long userId) {
        cache.invalidate(userId);
    }
}
