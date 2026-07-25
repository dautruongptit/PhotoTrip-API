package com.travelalbum.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class PhotoResponse {
    private Long id;
    private String originalName;
    private String url;
    private String thumbnailUrl;
    private Long size;
    private Integer width;
    private Integer height;
    private String uploadedBy;
    private LocalDateTime uploadedTime;
}
