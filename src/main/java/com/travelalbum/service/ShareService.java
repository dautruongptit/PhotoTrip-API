package com.travelalbum.service;

import com.travelalbum.dto.response.EventResponse;
import com.travelalbum.dto.response.PhotoResponse;
import com.travelalbum.dto.response.ShareLinkResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ShareService {
    ShareLinkResponse create(Long eventId, Long userId);
    EventResponse getEventByToken(String token);
    Page<PhotoResponse> listPhotosByToken(String token, Pageable pageable);
    void revoke(String token, Long requesterId, boolean isAdmin);
}
