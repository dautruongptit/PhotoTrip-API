package com.travelalbum.service.impl;

import com.travelalbum.dto.response.EventInviteResponse;
import com.travelalbum.entity.Event;
import com.travelalbum.entity.EventInvite;
import com.travelalbum.entity.EventMember;
import com.travelalbum.entity.User;
import com.travelalbum.enums.EventMemberRole;
import com.travelalbum.enums.InviteStatus;
import com.travelalbum.exception.BusinessException;
import com.travelalbum.exception.NotFoundException;
import com.travelalbum.repository.EventInviteRepository;
import com.travelalbum.repository.EventMemberRepository;
import com.travelalbum.repository.EventRepository;
import com.travelalbum.repository.UserRepository;
import com.travelalbum.service.AuditLogService;
import com.travelalbum.service.EventInviteService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EventInviteServiceImpl implements EventInviteService {

    private final EventInviteRepository eventInviteRepository;
    private final EventMemberRepository eventMemberRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public EventInviteResponse invite(Long eventId, String email, EventMemberRole role, Long inviterId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found"));
        if (!event.getOwnerId().equals(inviterId)) {
            throw new AccessDeniedException("Not the owner of this event");
        }
        User invitedUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found"));
        if (invitedUser.getId().equals(inviterId)) {
            throw new BusinessException("Cannot invite yourself", "CANNOT_INVITE_SELF");
        }
        if (eventMemberRepository.existsByEventIdAndUserId(eventId, invitedUser.getId())) {
            throw new BusinessException("User is already a member of this event", "ALREADY_MEMBER");
        }
        if (eventInviteRepository.findByEventIdAndInvitedUserId(eventId, invitedUser.getId()).isPresent()) {
            throw new BusinessException("User already has an invite for this event", "ALREADY_INVITED");
        }

        EventInvite invite = EventInvite.builder()
                .event(event)
                .invitedUserId(invitedUser.getId())
                .role(role)
                .status(InviteStatus.PENDING)
                .invitedBy(inviterId)
                .build();
        EventInvite saved = eventInviteRepository.save(invite);
        auditLogService.log(inviterId, "INVITE_MEMBER", "EVENT", eventId, null, null, "SUCCESS");

        return toResponse(saved, event.getName());
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventInviteResponse> listMine(Long userId) {
        // @Transactional bắt buộc — invite.getEvent().getName() chạm vào quan hệ LAZY,
        // giống lý do ShareServiceImpl.getEventByToken cần transaction mở sẵn.
        return eventInviteRepository.findByInvitedUserIdAndStatus(userId, InviteStatus.PENDING).stream()
                .map(i -> toResponse(i, i.getEvent().getName()))
                .toList();
    }

    @Override
    @Transactional
    public void accept(Long inviteId, Long userId) {
        EventInvite invite = getOwnPendingInvite(inviteId, userId);
        invite.setStatus(InviteStatus.ACCEPTED);
        invite.setRespondedAt(LocalDateTime.now());
        eventInviteRepository.save(invite);

        Long eventId = invite.getEvent().getId();
        if (!eventMemberRepository.existsByEventIdAndUserId(eventId, userId)) {
            EventMember member = EventMember.builder()
                    .event(invite.getEvent())
                    .userId(userId)
                    .role(invite.getRole())
                    .invitedBy(invite.getInvitedBy())
                    .build();
            eventMemberRepository.save(member);
        }
        auditLogService.log(userId, "ACCEPT_INVITE", "EVENT", eventId, null, null, "SUCCESS");
    }

    @Override
    @Transactional
    public void decline(Long inviteId, Long userId) {
        EventInvite invite = getOwnPendingInvite(inviteId, userId);
        invite.setStatus(InviteStatus.DECLINED);
        invite.setRespondedAt(LocalDateTime.now());
        eventInviteRepository.save(invite);
    }

    private EventInvite getOwnPendingInvite(Long inviteId, Long userId) {
        EventInvite invite = eventInviteRepository.findById(inviteId)
                .orElseThrow(() -> new NotFoundException("Invite not found"));
        if (!invite.getInvitedUserId().equals(userId)) {
            throw new AccessDeniedException("Not your invite");
        }
        if (invite.getStatus() != InviteStatus.PENDING) {
            throw new BusinessException("Invite already responded", "INVITE_ALREADY_RESPONDED");
        }
        return invite;
    }

    private EventInviteResponse toResponse(EventInvite invite, String eventName) {
        return EventInviteResponse.builder()
                .id(invite.getId())
                .eventId(invite.getEvent().getId())
                .eventName(eventName)
                .role(invite.getRole())
                .status(invite.getStatus())
                .createdAt(invite.getCreatedAt())
                .build();
    }
}
