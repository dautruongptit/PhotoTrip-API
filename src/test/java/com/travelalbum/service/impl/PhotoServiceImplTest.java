package com.travelalbum.service.impl;

import com.travelalbum.dto.response.PhotoResponse;
import com.travelalbum.dto.response.UploadResultResponse;
import com.travelalbum.entity.Event;
import com.travelalbum.entity.Photo;
import com.travelalbum.entity.User;
import com.travelalbum.exception.BusinessException;
import com.travelalbum.exception.NotFoundException;
import com.travelalbum.mapper.PhotoMapper;
import com.travelalbum.repository.EventRepository;
import com.travelalbum.repository.PhotoRepository;
import com.travelalbum.repository.UserRepository;
import com.travelalbum.service.AuditLogService;
import com.travelalbum.storage.StorageService;
import com.travelalbum.storage.StoredFile;
import com.travelalbum.dto.response.BatchDeleteResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PhotoServiceImplTest {

    @Mock private PhotoRepository photoRepository;
    @Mock private EventRepository eventRepository;
    @Mock private UserRepository userRepository;
    @Mock private StorageService storageService;
    @Mock private PhotoMapper photoMapper;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private PhotoServiceImpl photoService;

    private Event ownedEvent() {
        return Event.builder().id(10L).ownerId(1L).storageFolder("ev-folder").photoCount(0).totalSize(0L).build();
    }

    private User owner(long storageUsed, long storageQuota) {
        return User.builder().id(1L).storageFolder("dautruong_000001")
                .storageUsed(storageUsed).storageQuota(storageQuota).build();
    }

    @Test
    void uploadMultiple_throwsAccessDenied_whenRequesterNotEventOwner() {
        when(eventRepository.findById(10L)).thenReturn(Optional.of(ownedEvent()));
        MultipartFile[] files = { new MockMultipartFile("files", "a.jpg", "image/jpeg", "x".getBytes()) };

        assertThatThrownBy(() -> photoService.uploadMultiple(10L, files, 2L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void uploadMultiple_throwsBusinessException_whenTooManyFiles() {
        when(eventRepository.findById(10L)).thenReturn(Optional.of(ownedEvent()));
        MultipartFile one = new MockMultipartFile("files", "a.jpg", "image/jpeg", "x".getBytes());
        MultipartFile[] files = new MultipartFile[101];
        Arrays.fill(files, one);

        assertThatThrownBy(() -> photoService.uploadMultiple(10L, files, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("TOO_MANY_FILES"));
    }

    @Test
    void uploadMultiple_throwsBusinessException_whenQuotaExceeded() {
        when(eventRepository.findById(10L)).thenReturn(Optional.of(ownedEvent()));
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner(9_999_999L, 10_000_000L)));
        byte[] bigContent = new byte[2_000_000];
        MultipartFile[] files = { new MockMultipartFile("files", "big.jpg", "image/jpeg", bigContent) };

        assertThatThrownBy(() -> photoService.uploadMultiple(10L, files, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("QUOTA_EXCEEDED"));

        verify(photoRepository, never()).save(any());
    }

    @Test
    void uploadMultiple_savesPhotoAndUpdatesCounters_onHappyPath() {
        Event event = ownedEvent();
        User owner = owner(0L, 10_000_000L);
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(photoRepository.existsByEventIdAndOriginalName(10L, "IMG_001.jpg")).thenReturn(false);
        when(storageService.store(eq("dautruong_000001"), eq("ev-folder"), any()))
                .thenReturn(new StoredFile("uuid_IMG_001.jpg", "ev-folder/uuid_IMG_001.jpg", "checksum123", 1920, 1080));
        when(photoRepository.save(any(Photo.class))).thenAnswer(inv -> {
            Photo p = inv.getArgument(0);
            p.setId(500L);
            return p;
        });
        when(photoMapper.toResponse(any(Photo.class))).thenReturn(PhotoResponse.builder().id(500L).build());

        byte[] content = "fake-image-bytes".getBytes();
        MultipartFile[] files = { new MockMultipartFile("files", "IMG_001.jpg", "image/jpeg", content) };

        UploadResultResponse result = photoService.uploadMultiple(10L, files, 1L);

        assertThat(result.getUploaded()).isEqualTo(1);
        assertThat(result.getFailed()).isEqualTo(0);
        assertThat(event.getPhotoCount()).isEqualTo(1);
        assertThat(event.getTotalSize()).isEqualTo(content.length);
        assertThat(owner.getStorageUsed()).isEqualTo(content.length);
        verify(auditLogService).log(eq(1L), eq("UPLOAD"), eq("EVENT"), eq(10L), any(), any(), eq("SUCCESS"));
    }

    @Test
    void uploadMultiple_marksDuplicateNameAsFailed_insteadOfThrowing() {
        Event event = ownedEvent();
        User owner = owner(0L, 10_000_000L);
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(photoRepository.existsByEventIdAndOriginalName(10L, "dup.jpg")).thenReturn(true);

        MultipartFile[] files = { new MockMultipartFile("files", "dup.jpg", "image/jpeg", "x".getBytes()) };

        UploadResultResponse result = photoService.uploadMultiple(10L, files, 1L);

        assertThat(result.getUploaded()).isEqualTo(0);
        assertThat(result.getFailed()).isEqualTo(1);
        assertThat(result.getFailures().get(0).getErrorCode()).isEqualTo("PHOTO_EXIST");
        verify(photoRepository, never()).save(any());
    }

    @Test
    void delete_throwsAccessDenied_whenNotOwnerAndNotAdmin() {
        Event event = ownedEvent();
        Photo photo = Photo.builder().id(500L).event(event).path("ev-folder/x.jpg").size(1000L).build();
        when(photoRepository.findById(500L)).thenReturn(Optional.of(photo));

        assertThatThrownBy(() -> photoService.delete(500L, 2L, false))
                .isInstanceOf(AccessDeniedException.class);

        verify(storageService, never()).delete(anyString(), anyString());
    }

    @Test
    void delete_throwsNotFound_whenPhotoMissing() {
        when(photoRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> photoService.delete(999L, 1L, false)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void delete_decrementsCountersAndDeletesFile_whenOwner() {
        Event event = ownedEvent();
        event.setPhotoCount(3);
        event.setTotalSize(3000L);
        Photo photo = Photo.builder().id(500L).event(event).path("ev-folder/x.jpg").size(1000L).build();
        User owner = owner(3000L, 10_000_000L);

        when(photoRepository.findById(500L)).thenReturn(Optional.of(photo));
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));

        photoService.delete(500L, 1L, false);

        assertThat(event.getPhotoCount()).isEqualTo(2);
        assertThat(event.getTotalSize()).isEqualTo(2000L);
        assertThat(owner.getStorageUsed()).isEqualTo(2000L);
        verify(storageService).delete("dautruong_000001", "ev-folder/x.jpg");
        verify(photoRepository).delete(photo);
        verify(auditLogService).log(eq(1L), eq("DELETE_PHOTO"), eq("PHOTO"), eq(500L), any(), any(), eq("SUCCESS"));
    }

    @Test
    void deleteBatch_skipsFailedItems_andDeletesTheRest() {
        Event event = ownedEvent();
        Photo ownedPhoto = Photo.builder().id(500L).event(event).path("ev-folder/x.jpg").size(1000L).build();
        Event otherEvent = Event.builder().id(20L).ownerId(2L).storageFolder("other-folder").build();
        Photo notOwnedPhoto = Photo.builder().id(600L).event(otherEvent).path("other-folder/y.jpg").size(500L).build();
        User owner = owner(1000L, 10_000_000L);

        when(photoRepository.findById(500L)).thenReturn(Optional.of(ownedPhoto));
        when(photoRepository.findById(600L)).thenReturn(Optional.of(notOwnedPhoto));
        when(photoRepository.findById(999L)).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));

        BatchDeleteResponse result = photoService.deleteBatch(List.of(500L, 600L, 999L), 1L, false);

        assertThat(result.getDeletedIds()).containsExactly(500L);
        assertThat(result.getFailures()).hasSize(2);
        assertThat(result.getFailures()).anySatisfy(f -> {
            assertThat(f.getId()).isEqualTo(600L);
            assertThat(f.getErrorCode()).isEqualTo("ACCESS_DENIED");
        });
        assertThat(result.getFailures()).anySatisfy(f -> {
            assertThat(f.getId()).isEqualTo(999L);
            assertThat(f.getErrorCode()).isEqualTo("NOT_FOUND");
        });
        verify(photoRepository).delete(ownedPhoto);
        verify(photoRepository, never()).delete(notOwnedPhoto);
    }

    @Test
    void deleteBatch_throwsBusinessException_whenTooManyIds() {
        List<Long> ids = new ArrayList<>();
        for (long i = 0; i < 101; i++) {
            ids.add(i);
        }

        assertThatThrownBy(() -> photoService.deleteBatch(ids, 1L, false))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("TOO_MANY_ITEMS"));
    }

    @Test
    void deleteBatch_throwsBusinessException_whenEmpty() {
        assertThatThrownBy(() -> photoService.deleteBatch(List.of(), 1L, false))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("EMPTY_LIST"));
    }
}
