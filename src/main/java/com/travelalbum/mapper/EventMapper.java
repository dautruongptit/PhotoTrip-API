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

    public EventResponse toResponse(Event event) {
        String ownerName = userRepository.findById(event.getOwnerId())
            .map(userMapper::toDisplayName)
            .orElse(null);
        return EventResponse.builder()
            .id(event.getId())
            .name(event.getName())
            .description(event.getDescription())
            .ownerName(ownerName)
            .photoCount(event.getPhotoCount())
            .totalSize(event.getTotalSize())
            .createdAt(event.getCreatedAt())
            .build();
    }
}
