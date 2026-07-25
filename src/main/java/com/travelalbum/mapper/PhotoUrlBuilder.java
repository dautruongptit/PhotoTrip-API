package com.travelalbum.mapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Sinh URL công khai (đi qua API kiểm soát quyền) cho ảnh/thumbnail —
 * KHÔNG bao giờ lộ path vật lý thật, xem SEC-01/SEC-02.
 */
@Component
public class PhotoUrlBuilder {

    @Value("${app.api-base-url}")
    private String apiBaseUrl;

    public String buildUrl(Long photoId) {
        return apiBaseUrl + "/api/photos/download/" + photoId;
    }

    public String buildThumbnailUrl(Long photoId) {
        return apiBaseUrl + "/api/photos/" + photoId + "/thumbnail";
    }
}
