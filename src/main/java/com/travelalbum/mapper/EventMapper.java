package com.travelalbum.mapper;

import com.travelalbum.dto.response.EventResponse;
import com.travelalbum.entity.Event;
import com.travelalbum.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EventMapper {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PhotoUrlBuilder photoUrlBuilder;

    public EventResponse toResponse(Event event) {
        String ownerName = userRepository.findById(event.getOwnerId())
                .map(userMapper::toDisplayName)
                .orElse(null);
        String coverImageUrl = event.getCoverImagePath() != null
                ? photoUrlBuilder.buildEventCoverUrl(event.getId())
                : null;
        return EventResponse.builder()
                .id(event.getId())
                .name(event.getName())
                .description(event.getDescription())
                .ownerName(ownerName)
                .startDate(event.getStartDate())
                .endDate(event.getEndDate())
                .location(event.getLocation())
                .coverImageUrl(coverImageUrl)
                .photoCount(event.getPhotoCount())
                .totalSize(event.getTotalSize())
                .createdAt(event.getCreatedAt())
                .build();
    }
}