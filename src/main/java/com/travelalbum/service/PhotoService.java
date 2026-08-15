package com.travelalbum.service;

import com.travelalbum.dto.response.BatchDeleteResponse;
import com.travelalbum.dto.response.PhotoResponse;
import com.travelalbum.dto.response.UploadResultResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface PhotoService {
    UploadResultResponse uploadMultiple(Long eventId, MultipartFile[] files, Long userId);
    Page<PhotoResponse> listByEvent(Long eventId, Pageable pageable);
    Page<PhotoResponse> search(String keyword, Pageable pageable, Long requesterId, boolean isAdmin);
    void delete(Long photoId, Long requesterId, boolean isAdmin);
    BatchDeleteResponse deleteBatch(List<Long> photoIds, Long requesterId, boolean isAdmin);
}
