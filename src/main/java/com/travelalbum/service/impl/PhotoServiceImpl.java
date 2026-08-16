package com.travelalbum.service.impl;

import com.travelalbum.dto.response.BatchDeleteFailure;
import com.travelalbum.dto.response.BatchDeleteResponse;
import com.travelalbum.dto.response.PhotoResponse;
import com.travelalbum.dto.response.UploadFailure;
import com.travelalbum.dto.response.UploadResultResponse;
import com.travelalbum.entity.Event;
import com.travelalbum.entity.Photo;
import com.travelalbum.entity.User;
import com.travelalbum.enums.EventMemberRole;
import com.travelalbum.exception.BusinessException;
import com.travelalbum.exception.NotFoundException;
import com.travelalbum.mapper.PhotoMapper;
import com.travelalbum.repository.EventRepository;
import com.travelalbum.repository.EventMemberRepository;
import com.travelalbum.repository.PhotoRepository;
import com.travelalbum.repository.UserRepository;
import com.travelalbum.service.AuditLogService;
import com.travelalbum.service.PhotoService;
import com.travelalbum.storage.StorageService;
import com.travelalbum.storage.StoredFile;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PhotoServiceImpl implements PhotoService {

    private static final long MAX_SIZE = 10L * 1024 * 1024;
    private static final int MAX_FILES = 100;
    private static final int MAX_BATCH_DELETE = 100;
    private static final Set<String> ALLOWED_MIME = Set.of("image/jpeg", "image/png", "image/webp");

    private final PhotoRepository photoRepository;
    private final EventRepository eventRepository;
    private final EventMemberRepository eventMemberRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final PhotoMapper photoMapper;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public UploadResultResponse uploadMultiple(Long eventId, MultipartFile[] files, Long userId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found"));
        boolean isOwner = event.getOwnerId().equals(userId);
        boolean isEditor = eventMemberRepository.existsByEventIdAndUserIdAndRole(eventId, userId, EventMemberRole.EDITOR);
        if (!isOwner && !isEditor) {
            throw new AccessDeniedException("Not allowed to upload to this event");
        }
        if (files.length > MAX_FILES) {
            throw new BusinessException("Too many files in one request", "TOO_MANY_FILES");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        long incomingSize = Arrays.stream(files).mapToLong(MultipartFile::getSize).sum();
        if (user.getStorageUsed() + incomingSize > user.getStorageQuota()) {
            throw new BusinessException("Storage quota exceeded", "QUOTA_EXCEEDED");
        }

        List<PhotoResponse> uploaded = new ArrayList<>();
        List<UploadFailure> failed = new ArrayList<>();
        long actualUploadedSize = 0L;

        for (MultipartFile file : files) {
            try {
                validate(file);
                if (photoRepository.existsByEventIdAndOriginalName(eventId, file.getOriginalFilename())) {
                    failed.add(new UploadFailure(file.getOriginalFilename(), "PHOTO_EXIST"));
                    continue;
                }
                StoredFile stored = storageService.store(user.getStorageFolder(), event.getStorageFolder(), file);
                Photo photo = Photo.builder()
                        .event(event)
                        .fileName(stored.fileName())
                        .originalName(file.getOriginalFilename())
                        .path(stored.relativePath())
                        .size(file.getSize())
                        .mimeType(file.getContentType())
                        .width(stored.width())
                        .height(stored.height())
                        .checksum(stored.checksum())
                        .uploadedBy(userId)
                        .build();
                Photo saved = photoRepository.save(photo);
                uploaded.add(photoMapper.toResponse(saved));
                actualUploadedSize += file.getSize();
            } catch (BusinessException ex) {
                failed.add(new UploadFailure(file.getOriginalFilename(), ex.getErrorCode()));
            } catch (Exception ex) {
                failed.add(new UploadFailure(file.getOriginalFilename(), "UPLOAD_FAILED"));
            }
        }

        event.setPhotoCount(event.getPhotoCount() + uploaded.size());
        event.setTotalSize(event.getTotalSize() + actualUploadedSize);
        eventRepository.save(event);

        user.setStorageUsed(user.getStorageUsed() + actualUploadedSize);
        userRepository.save(user);

        auditLogService.log(userId, "UPLOAD", "EVENT", eventId, null, null, "SUCCESS");
        return new UploadResultResponse(uploaded.size(), failed.size(), uploaded, failed);
    }

    @Override
    public Page<PhotoResponse> listByEvent(Long eventId, Pageable pageable) {
        return photoRepository.findByEventId(eventId, pageable).map(photoMapper::toResponse);
    }

    @Override
    public Page<PhotoResponse> search(String keyword, Pageable pageable, Long requesterId, boolean isAdmin) {
        Page<Photo> page = isAdmin
                ? photoRepository.search(keyword, pageable)
                : photoRepository.searchByOwner(keyword, requesterId, pageable);
        return page.map(photoMapper::toResponse);
    }

    @Override
    @Transactional
    public void delete(Long photoId, Long requesterId, boolean isAdmin) {
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new NotFoundException("Photo not found"));
        Long ownerId = photo.getEvent().getOwnerId();
        if (!isAdmin && !ownerId.equals(requesterId)) {
            throw new AccessDeniedException("Not the owner of this photo");
        }
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new NotFoundException("Owner not found"));
        storageService.delete(owner.getStorageFolder(), photo.getPath());

        Event event = photo.getEvent();
        event.setPhotoCount(Math.max(0, event.getPhotoCount() - 1));
        event.setTotalSize(Math.max(0, event.getTotalSize() - photo.getSize()));
        eventRepository.save(event);

        owner.setStorageUsed(Math.max(0, owner.getStorageUsed() - photo.getSize()));
        userRepository.save(owner);

        photoRepository.delete(photo);
        auditLogService.log(requesterId, "DELETE_PHOTO", "PHOTO", photoId, null, null, "SUCCESS");
    }

    @Override
    @Transactional
    public BatchDeleteResponse deleteBatch(List<Long> photoIds, Long requesterId, boolean isAdmin) {
        if (photoIds.isEmpty()) {
            throw new BusinessException("No photo id provided", "EMPTY_LIST");
        }
        if (photoIds.size() > MAX_BATCH_DELETE) {
            throw new BusinessException("Too many photos in one request", "TOO_MANY_ITEMS");
        }

        List<Long> deletedIds = new ArrayList<>();
        List<BatchDeleteFailure> failures = new ArrayList<>();
        for (Long photoId : photoIds) {
            try {
                // Gọi lại delete() để tái dùng nguyên logic check quyền/dọn storage/audit log —
                // item nào lỗi thì bỏ qua, không làm hỏng cả batch.
                delete(photoId, requesterId, isAdmin);
                deletedIds.add(photoId);
            } catch (NotFoundException ex) {
                failures.add(new BatchDeleteFailure(photoId, "NOT_FOUND"));
            } catch (AccessDeniedException ex) {
                failures.add(new BatchDeleteFailure(photoId, "ACCESS_DENIED"));
            }
        }
        return new BatchDeleteResponse(deletedIds, failures);
    }

    private void validate(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException("Empty file", "EMPTY_FILE");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException("File too large", "FILE_TOO_LARGE");
        }
        if (!ALLOWED_MIME.contains(file.getContentType())) {
            throw new BusinessException("Invalid file type", "INVALID_TYPE");
        }
    }
}