package com.travelalbum.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class EventResponse {
    private Long id;
    private String name;
    private String description;
    private String ownerName;
    private Long ownerId;
    private LocalDate startDate;
    private LocalDate endDate;
    private String location;
    private String coverImageUrl;
    private int photoCount;
    private long totalSize;
    private LocalDateTime createdAt;
}