package com.travelalbum.service.impl;

import com.travelalbum.dto.request.CreateEventRequest;
import com.travelalbum.dto.request.UpdateEventRequest;
import com.travelalbum.dto.response.EventResponse;
import com.travelalbum.entity.Event;
import com.travelalbum.entity.User;
import com.travelalbum.exception.BusinessException;
import com.travelalbum.exception.NotFoundException;
import com.travelalbum.mapper.EventMapper;
import com.travelalbum.repository.EventRepository;
import com.travelalbum.repository.UserRepository;
import com.travelalbum.service.AuditLogService;
import com.travelalbum.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceImplTest {

    @Mock private EventRepository eventRepository;
    @Mock private UserRepository userRepository;
    @Mock private EventMapper eventMapper;
    @Mock private StorageService storageService;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private EventServiceImpl eventService;

    private CreateEventRequest createReq() {
        CreateEventRequest req = new CreateEventRequest();
        req.setName("Da Lat 2026");
        req.setDescription("Chuyen di cong ty");
        req.setStartDate(LocalDate.of(2026, 7, 15));
        req.setEndDate(LocalDate.of(2026, 7, 18));
        req.setLocation("Da Lat, Lam Dong");
        return req;
    }

    @Test
    void create_throwsBusinessException_whenNameAlreadyExists() {
        when(eventRepository.existsByNameIgnoreCase("Da Lat 2026")).thenReturn(true);

        assertThatThrownBy(() -> eventService.create(createReq(), null, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("EVENT_EXIST"));

        verify(eventRepository, never()).save(any());
    }

    @Test
    void create_throwsNotFound_whenOwnerMissing() {
        when(eventRepository.existsByNameIgnoreCase(anyString())).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.create(createReq(), null, 1L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void create_assignsStorageFolderToOwner_whenFirstEvent() {
        User owner = User.builder().id(1L).email("dautruong@gmail.com").storageFolder(null).build();

        when(eventRepository.existsByNameIgnoreCase(anyString())).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> {
            Event e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(100L);
            }
            return e;
        });
        when(eventMapper.toResponse(any(Event.class)))
                .thenReturn(EventResponse.builder().id(100L).name("Da Lat 2026").build());

        EventResponse response = eventService.create(createReq(), null, 1L);

        assertThat(response.getId()).isEqualTo(100L);
        // SEC-26: storageFolder của owner chỉ được gán 1 lần, dùng lại cho mọi event sau này
        assertThat(owner.getStorageFolder()).isEqualTo("dautruong_000001");
        verify(storageService).createUserRootFolder("dautruong_000001");
        verify(storageService).createEventFolder(eq("dautruong_000001"), anyString());
        verify(eventRepository, times(2)).save(any(Event.class));
        verify(auditLogService).log(eq(1L), eq("CREATE_EVENT"), eq("EVENT"), eq(100L), any(), any(), eq("SUCCESS"));
    }

    @Test
    void create_doesNotRecomputeStorageFolder_whenOwnerAlreadyHasOne() {
        User owner = User.builder().id(1L).email("dautruong@gmail.com").storageFolder("dautruong_000001").build();

        when(eventRepository.existsByNameIgnoreCase(anyString())).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> {
            Event e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(101L);
            }
            return e;
        });
        when(eventMapper.toResponse(any(Event.class))).thenReturn(EventResponse.builder().id(101L).build());

        eventService.create(createReq(), null, 1L);

        verify(storageService, never()).createUserRootFolder(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void update_throwsAccessDenied_whenRequesterIsNotOwner() {
        Event existing = Event.builder().id(5L).ownerId(1L).name("Old name").build();
        when(eventRepository.findById(5L)).thenReturn(Optional.of(existing));

        UpdateEventRequest req = new UpdateEventRequest();
        req.setName("New name");
        req.setStartDate(LocalDate.now());
        req.setLocation("Somewhere");

        assertThatThrownBy(() -> eventService.update(5L, req, 2L))
                .isInstanceOf(AccessDeniedException.class);

        verify(eventRepository, never()).save(any());
    }

    @Test
    void delete_throwsAccessDenied_whenNotOwnerAndNotAdmin() {
        Event existing = Event.builder().id(5L).ownerId(1L).storageFolder("ev-folder").build();
        when(eventRepository.findById(5L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> eventService.delete(5L, 2L, false))
                .isInstanceOf(AccessDeniedException.class);

        verify(storageService, never()).deleteEventFolder(anyString(), anyString());
        verify(eventRepository, never()).delete(any());
    }

    @Test
    void delete_succeeds_whenRequesterIsOwner() {
        Event existing = Event.builder().id(5L).ownerId(1L).storageFolder("ev-folder").build();
        User owner = User.builder().id(1L).storageFolder("dautruong_000001").build();
        when(eventRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));

        eventService.delete(5L, 1L, false);

        verify(storageService).deleteEventFolder("dautruong_000001", "ev-folder");
        verify(eventRepository).delete(existing);
        verify(auditLogService).log(eq(1L), eq("DELETE_EVENT"), eq("EVENT"), eq(5L), any(), any(), eq("SUCCESS"));
    }

    @Test
    void delete_succeeds_whenRequesterIsAdminButNotOwner() {
        Event existing = Event.builder().id(5L).ownerId(1L).storageFolder("ev-folder").build();
        User owner = User.builder().id(1L).storageFolder("dautruong_000001").build();
        when(eventRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));

        eventService.delete(5L, 999L, true);

        verify(eventRepository).delete(existing);
    }

    @Test
    void getById_throwsNotFound_whenMissing() {
        when(eventRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.getById(42L)).isInstanceOf(NotFoundException.class);
    }
}
