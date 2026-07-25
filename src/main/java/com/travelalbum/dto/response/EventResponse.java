package com.travelalbum.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class EventResponse {
    private Long id;
    private String name;
    private String description;
    private String ownerName;
    private int photoCount;
    private long totalSize;
    private LocalDateTime createdAt;
}
