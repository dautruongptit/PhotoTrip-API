package com.travelalbum.service.impl;

import com.travelalbum.dto.response.EventResponse;
import com.travelalbum.dto.response.PhotoResponse;
import com.travelalbum.dto.response.ShareLinkResponse;
import com.travelalbum.entity.Event;
import com.travelalbum.entity.EventMember;
import com.travelalbum.entity.ShareLink;
import com.travelalbum.enums.EventMemberRole;
import com.travelalbum.exception.NotFoundException;
import com.travelalbum.mapper.EventMapper;
import com.travelalbum.mapper.PhotoMapper;
import com.travelalbum.repository.EventMemberRepository;
import com.travelalbum.repository.EventRepository;
import com.travelalbum.repository.PhotoRepository;
import com.travelalbum.repository.ShareLinkRepository;
import com.travelalbum.service.AuditLogService;
import com.travelalbum.service.ShareService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ShareServiceImpl implements ShareService {

    private final ShareLinkRepository shareLinkRepository;
    private final EventRepository eventRepository;
    private final PhotoRepository photoRepository;
    private final EventMemberRepository eventMemberRepository;
    private final EventMapper eventMapper;
    private final PhotoMapper photoMapper;
    private final AuditLogService auditLogService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    @Transactional
    public ShareLinkResponse create(Long eventId, Long userId, EventMemberRole role) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new NotFoundException("Event not found"));
        if (!event.getOwnerId().equals(userId)) {
            throw new AccessDeniedException("Not the owner of this event");
        }
        EventMemberRole linkRole = role != null ? role : EventMemberRole.VIEWER;
        String token = generateUniqueToken();
        ShareLink link = ShareLink.builder()
            .event(event)
            .token(token)
            .createdBy(userId)
            .active(true)
            .role(linkRole)
            .build();
        shareLinkRepository.save(link);
        auditLogService.log(userId, "SHARE_EVENT", "EVENT", eventId, null, null, "SUCCESS");

        return ShareLinkResponse.builder()
            .token(token)
            .shareUrl(frontendUrl + "/share/" + token)
            .active(true)
            .role(linkRole)
            .build();
    }

    @Override
    @Transactional(readOnly = true)
    public EventResponse getEventByToken(String token) {
        // @Transactional bắt buộc ở đây: ShareLink.event là quan hệ LAZY, eventMapper
        // đọc nhiều field của Event (name, description, startDate...) chứ không chỉ id
        // — nếu không có transaction mở sẵn, proxy sẽ ném LazyInitializationException
        // (open-in-view: false, mỗi repository call tự đóng session ngay khi trả về).
        ShareLink link = shareLinkRepository.findByTokenAndActiveTrue(token)
            .filter(l -> l.getExpiredAt() == null || l.getExpiredAt().isAfter(LocalDateTime.now()))
            .orElseThrow(() -> new NotFoundException("Share link not found or expired"));
        return eventMapper.toResponse(link.getEvent());
    }

    @Override
    public Page<PhotoResponse> listPhotosByToken(String token, Pageable pageable) {
        ShareLink link = shareLinkRepository.findByTokenAndActiveTrue(token)
            .filter(l -> l.getExpiredAt() == null || l.getExpiredAt().isAfter(LocalDateTime.now()))
            .orElseThrow(() -> new NotFoundException("Share link not found or expired"));
        return photoRepository.findByEventId(link.getEvent().getId(), pageable).map(photoMapper::toResponse);
    }

    @Override
    @Transactional
    public void revoke(String token, Long requesterId, boolean isAdmin) {
        ShareLink link = shareLinkRepository.findByTokenAndActiveTrue(token)
            .orElseThrow(() -> new NotFoundException("Share link not found"));
        if (!isAdmin && !link.getEvent().getOwnerId().equals(requesterId)) {
            throw new AccessDeniedException("Not the owner of this event");
        }
        link.setActive(false);
        shareLinkRepository.save(link);
        auditLogService.log(requesterId, "REVOKE_SHARE", "EVENT", link.getEvent().getId(), null, null, "SUCCESS");
    }

    @Override
    @Transactional
    public void joinByToken(String token, Long userId) {
        ShareLink link = shareLinkRepository.findByTokenAndActiveTrue(token)
            .filter(l -> l.getExpiredAt() == null || l.getExpiredAt().isAfter(LocalDateTime.now()))
            .orElseThrow(() -> new NotFoundException("Share link not found or expired"));
        Long eventId = link.getEvent().getId();
        if (eventMemberRepository.existsByEventIdAndUserId(eventId, userId)) {
            return;
        }
        EventMember member = EventMember.builder()
            .event(link.getEvent())
            .userId(userId)
            .role(link.getRole())
            .invitedBy(link.getCreatedBy())
            .build();
        eventMemberRepository.save(member);
        auditLogService.log(userId, "JOIN_EVENT", "EVENT", eventId, null, null, "SUCCESS");
    }

    private String generateUniqueToken() {
        String token;
        do {
            token = RandomStringUtils.randomAlphanumeric(12);
        } while (shareLinkRepository.findByTokenAndActiveTrue(token).isPresent());
        return token;
    }
}
