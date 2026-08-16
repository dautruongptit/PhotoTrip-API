package com.travelalbum.service.impl;

import com.travelalbum.dto.response.EventInviteResponse;
import com.travelalbum.entity.Event;
import com.travelalbum.entity.EventInvite;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventInviteServiceImplTest {

    @Mock private EventInviteRepository eventInviteRepository;
    @Mock private EventMemberRepository eventMemberRepository;
    @Mock private EventRepository eventRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private EventInviteServiceImpl eventInviteService;

    private Event event() {
        return Event.builder().id(10L).ownerId(1L).name("Da Lat Trip").build();
    }

    @Test
    void invite_throwsAccessDenied_whenRequesterNotOwner() {
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event()));

        assertThatThrownBy(() -> eventInviteService.invite(10L, "member@test.com", EventMemberRole.EDITOR, 2L))
                .isInstanceOf(AccessDeniedException.class);
        verify(eventInviteRepository, never()).save(any());
    }

    @Test
    void invite_throwsNotFound_whenEmailUnknown() {
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event()));
        when(userRepository.findByEmail("nobody@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventInviteService.invite(10L, "nobody@test.com", EventMemberRole.EDITOR, 1L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void invite_throwsBusinessException_whenInvitingSelf() {
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event()));
        when(userRepository.findByEmail("owner@test.com")).thenReturn(Optional.of(User.builder().id(1L).email("owner@test.com").build()));

        assertThatThrownBy(() -> eventInviteService.invite(10L, "owner@test.com", EventMemberRole.EDITOR, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("CANNOT_INVITE_SELF"));
    }

    @Test
    void invite_throwsBusinessException_whenAlreadyMember() {
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event()));
        when(userRepository.findByEmail("member@test.com")).thenReturn(Optional.of(User.builder().id(2L).email("member@test.com").build()));
        when(eventMemberRepository.existsByEventIdAndUserId(10L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> eventInviteService.invite(10L, "member@test.com", EventMemberRole.EDITOR, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("ALREADY_MEMBER"));
    }

    @Test
    void invite_throwsBusinessException_whenAlreadyInvited() {
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event()));
        when(userRepository.findByEmail("member@test.com")).thenReturn(Optional.of(User.builder().id(2L).email("member@test.com").build()));
        when(eventMemberRepository.existsByEventIdAndUserId(10L, 2L)).thenReturn(false);
        when(eventInviteRepository.findByEventIdAndInvitedUserId(10L, 2L))
                .thenReturn(Optional.of(EventInvite.builder().id(99L).build()));

        assertThatThrownBy(() -> eventInviteService.invite(10L, "member@test.com", EventMemberRole.EDITOR, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("ALREADY_INVITED"));
    }

    @Test
    void invite_createsPendingInvite_onHappyPath() {
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event()));
        when(userRepository.findByEmail("member@test.com")).thenReturn(Optional.of(User.builder().id(2L).email("member@test.com").build()));
        when(eventMemberRepository.existsByEventIdAndUserId(10L, 2L)).thenReturn(false);
        when(eventInviteRepository.findByEventIdAndInvitedUserId(10L, 2L)).thenReturn(Optional.empty());
        when(eventInviteRepository.save(any(EventInvite.class))).thenAnswer(inv -> {
            EventInvite i = inv.getArgument(0);
            i.setId(500L);
            return i;
        });

        EventInviteResponse response = eventInviteService.invite(10L, "member@test.com", EventMemberRole.EDITOR, 1L);

        assertThat(response.getId()).isEqualTo(500L);
        assertThat(response.getEventId()).isEqualTo(10L);
        assertThat(response.getEventName()).isEqualTo("Da Lat Trip");
        assertThat(response.getRole()).isEqualTo(EventMemberRole.EDITOR);
        assertThat(response.getStatus()).isEqualTo(InviteStatus.PENDING);
        verify(auditLogService).log(eq(1L), eq("INVITE_MEMBER"), eq("EVENT"), eq(10L), any(), any(), eq("SUCCESS"));
    }

    @Test
    void listMine_returnsOnlyPendingInvites() {
        EventInvite invite = EventInvite.builder().id(1L).event(event()).role(EventMemberRole.VIEWER).status(InviteStatus.PENDING).build();
        when(eventInviteRepository.findByInvitedUserIdAndStatus(2L, InviteStatus.PENDING)).thenReturn(List.of(invite));

        List<EventInviteResponse> result = eventInviteService.listMine(2L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEventName()).isEqualTo("Da Lat Trip");
    }

    @Test
    void accept_createsMemberAndMarksAccepted() {
        EventInvite invite = EventInvite.builder().id(1L).event(event()).invitedUserId(2L)
                .role(EventMemberRole.EDITOR).status(InviteStatus.PENDING).invitedBy(1L).build();
        when(eventInviteRepository.findById(1L)).thenReturn(Optional.of(invite));
        when(eventMemberRepository.existsByEventIdAndUserId(10L, 2L)).thenReturn(false);

        eventInviteService.accept(1L, 2L);

        assertThat(invite.getStatus()).isEqualTo(InviteStatus.ACCEPTED);
        assertThat(invite.getRespondedAt()).isNotNull();
        ArgumentCaptor<com.travelalbum.entity.EventMember> captor = ArgumentCaptor.forClass(com.travelalbum.entity.EventMember.class);
        verify(eventMemberRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(2L);
        assertThat(captor.getValue().getRole()).isEqualTo(EventMemberRole.EDITOR);
    }

    @Test
    void accept_throwsAccessDenied_whenNotOwnInvite() {
        EventInvite invite = EventInvite.builder().id(1L).event(event()).invitedUserId(2L).status(InviteStatus.PENDING).build();
        when(eventInviteRepository.findById(1L)).thenReturn(Optional.of(invite));

        assertThatThrownBy(() -> eventInviteService.accept(1L, 3L)).isInstanceOf(AccessDeniedException.class);
        verify(eventMemberRepository, never()).save(any());
    }

    @Test
    void accept_throwsBusinessException_whenAlreadyResponded() {
        EventInvite invite = EventInvite.builder().id(1L).event(event()).invitedUserId(2L).status(InviteStatus.DECLINED).build();
        when(eventInviteRepository.findById(1L)).thenReturn(Optional.of(invite));

        assertThatThrownBy(() -> eventInviteService.accept(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("INVITE_ALREADY_RESPONDED"));
    }

    @Test
    void decline_marksDeclined_withoutCreatingMember() {
        EventInvite invite = EventInvite.builder().id(1L).event(event()).invitedUserId(2L).status(InviteStatus.PENDING).build();
        when(eventInviteRepository.findById(1L)).thenReturn(Optional.of(invite));

        eventInviteService.decline(1L, 2L);

        assertThat(invite.getStatus()).isEqualTo(InviteStatus.DECLINED);
        verify(eventMemberRepository, never()).save(any());
    }
}
