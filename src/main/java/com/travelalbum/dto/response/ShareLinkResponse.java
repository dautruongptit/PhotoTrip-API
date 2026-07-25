package com.travelalbum.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ShareLinkResponse {
    private String token;
    private String shareUrl;
    private LocalDateTime expiredAt;
    private boolean active;
}
