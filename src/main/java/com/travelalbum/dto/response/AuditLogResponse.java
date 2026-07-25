package com.travelalbum.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AuditLogResponse {
    private LocalDateTime time;
    private String userEmail;
    private String action;
    private String ip;
    private String userAgent;
    private String result;
}
