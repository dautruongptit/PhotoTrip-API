package com.travelalbum.service.impl;

import com.travelalbum.dto.request.CreateEventRequest;
import com.travelalbum.dto.request.UpdateEventRequest;
import com.travelalbum.dto.response.EventResponse;
import com.travelalbum.entity.Event;
import com.travelalbum.exception.BusinessException;
import com.travelalbum.exception.NotFoundException;
import com.travelalbum.mapper.EventMapper;
import com.travelalbum.repository.EventRepository;
import com.travelalbum.service.AuditLogService;
import com.travelalbum.service.EventService;
import com.travelalbum.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final EventMapper eventMapper;
    private final StorageService storageService;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public EventResponse create(CreateEventRequest req, Long ownerId) {
        if (eventRepository.existsByNameIgnoreCase(req.getName())) {
            throw new BusinessException("Event name already exists", "EVENT_EXIST");
        }
        Event event = Event.builder()
            .name(req.getName())
            .description(req.getDescription())
            .ownerId(ownerId)
            .storageFolder("Event_" + System.currentTimeMillis())
            .photoCount(0)
            .totalSize(0L)
            .build();
        storageService.createEventFolder(ownerId, event.getStorageFolder());
        Event saved = eventRepository.save(event);
        auditLogService.log(ownerId, "CREATE_EVENT", "EVENT", saved.getId(), null, null, "SUCCESS");
        return eventMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public EventResponse update(Long id, UpdateEventRequest req, Long ownerId) {
        Event event = eventRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Event not found"));
        if (!event.getOwnerId().equals(ownerId)) {
            throw new AccessDeniedException("Not the owner of this event");
        }
        event.setName(req.getName());
        event.setDescription(req.getDescription());
        Event saved = eventRepository.save(event);
        auditLogService.log(ownerId, "UPDATE_EVENT", "EVENT", id, null, null, "SUCCESS");
        return eventMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(Long id, Long requesterId, boolean isAdmin) {
        Event event = eventRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Event not found"));
        if (!isAdmin && !event.getOwnerId().equals(requesterId)) {
            throw new AccessDeniedException("Not the owner of this event");
        }
        storageService.deleteEventFolder(event.getOwnerId(), event.getStorageFolder());
        eventRepository.delete(event);
        auditLogService.log(requesterId, "DELETE_EVENT", "EVENT", id, null, null, "SUCCESS");
    }

    @Override
    public EventResponse getById(Long id) {
        Event event = eventRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Event not found"));
        return eventMapper.toResponse(event);
    }

    @Override
    public Page<EventResponse> list(Pageable pageable) {
        return eventRepository.findAll(pageable).map(eventMapper::toResponse);
    }

    @Override
    public Page<EventResponse> search(String keyword, Pageable pageable) {
        return eventRepository.search(keyword, pageable).map(eventMapper::toResponse);
    }
}
