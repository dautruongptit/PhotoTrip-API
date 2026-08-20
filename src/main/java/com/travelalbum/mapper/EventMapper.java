package com.travelalbum.mapper;

import com.travelalbum.dto.response.EventResponse;
import com.travelalbum.entity.Event;
import com.travelalbum.entity.User;
import com.travelalbum.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        return toResponse(event, ownerName);
    }

    /**
     * Map cả danh sách event trong 1 lần gọi, chỉ query owner 1 lần cho cả danh sách
     * (thay vì 1 query/event như toResponse(Event) — tránh N+1 khi list/search events).
     */
    public List<EventResponse> toResponseList(List<Event> events) {
        if (events.isEmpty()) {
            return List.of();
        }
        List<Long> ownerIds = events.stream().map(Event::getOwnerId).distinct().toList();
        Map<Long, String> ownerNameById = userRepository.findAllById(ownerIds).stream()
                .collect(Collectors.toMap(User::getId, userMapper::toDisplayName));
        return events.stream()
                .map(event -> toResponse(event, ownerNameById.get(event.getOwnerId())))
                .toList();
    }

    private EventResponse toResponse(Event event, String ownerName) {
        String coverImageUrl = event.getCoverImagePath() != null
                ? photoUrlBuilder.buildEventCoverUrl(event.getId())
                : null;
        return EventResponse.builder()
                .id(event.getId())
                .name(event.getName())
                .description(event.getDescription())
                .ownerName(ownerName)
                .ownerId(event.getOwnerId())
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