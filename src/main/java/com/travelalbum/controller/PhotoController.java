package com.travelalbum.controller;

import com.travelalbum.audit.Auditable;
import com.travelalbum.common.ApiResponse;
import com.travelalbum.dto.response.PhotoResponse;
import com.travelalbum.dto.response.UploadResultResponse;
import com.travelalbum.entity.Photo;
import com.travelalbum.exception.NotFoundException;
import com.travelalbum.repository.PhotoRepository;
import com.travelalbum.security.userdetails.UserPrincipal;
import com.travelalbum.service.AuditLogService;
import com.travelalbum.service.PhotoService;
import com.travelalbum.storage.StorageService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequiredArgsConstructor
public class PhotoController {

    private final PhotoService photoService;
    private final PhotoRepository photoRepository;
    private final StorageService storageService;
    private final AuditLogService auditLogService;

    @Auditable(action = "UPLOAD", targetType = "EVENT")
    @PostMapping(value = "/api/events/{eventId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<UploadResultResponse> upload(@PathVariable Long eventId,
            @RequestParam("files") org.springframework.web.multipart.MultipartFile[] files,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success("Upload processed",
            photoService.uploadMultiple(eventId, files, principal.getId()));
    }

    @GetMapping("/api/events/{eventId}/photos")
    public ApiResponse<Page<PhotoResponse>> listByEvent(@PathVariable Long eventId, Pageable pageable) {
        return ApiResponse.success("OK", photoService.listByEvent(eventId, pageable));
    }

    // GET /api/photos/search đã chuyển sang SearchController riêng — xem SEC-15

    @Auditable(action = "DELETE_PHOTO", targetType = "PHOTO")
    @DeleteMapping("/api/photos/{id}")
    @PreAuthorize("hasRole('ADMIN') or @photoSecurity.isOwner(#id, authentication)")
    public ApiResponse<Void> delete(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        photoService.delete(id, principal.getId(), principal.isAdmin());
        return ApiResponse.success("Photo deleted", null);
    }

    @Auditable(action = "DOWNLOAD", targetType = "PHOTO")
    @GetMapping("/api/photos/download/{id}")
    public void download(@PathVariable Long id, HttpServletResponse response) throws IOException {
        Photo photo = photoRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Photo not found"));
        Resource resource = storageService.load(photo.getEvent().getOwnerId(), photo.getPath());
        response.setContentType(photo.getMimeType());
        response.setHeader("Content-Disposition", "attachment; filename=\"" + photo.getOriginalName() + "\"");
        try (InputStream in = resource.getInputStream(); OutputStream out = response.getOutputStream()) {
            in.transferTo(out);
        }
    }

    @Auditable(action = "DOWNLOAD", targetType = "PHOTO")
    @PostMapping("/api/photos/download-zip")
    public void downloadZip(@RequestParam List<Long> ids, HttpServletResponse response) throws IOException {
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=photos.zip");
        List<Photo> photos = photoRepository.findByIdIn(ids);
        try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
            for (Photo photo : photos) {
                zos.putNextEntry(new ZipEntry(photo.getOriginalName()));
                Resource resource = storageService.load(photo.getEvent().getOwnerId(), photo.getPath());
                try (InputStream is = resource.getInputStream()) {
                    is.transferTo(zos);
                }
                zos.closeEntry();
            }
        }
    }
}
