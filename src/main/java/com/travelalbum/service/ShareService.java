package com.travelalbum.service;

import com.travelalbum.dto.response.EventResponse;
import com.travelalbum.dto.response.PhotoResponse;
import com.travelalbum.dto.response.ShareLinkResponse;
import com.travelalbum.enums.EventMemberRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ShareService {
    ShareLinkResponse create(Long eventId, Long userId, EventMemberRole role);
    EventResponse getEventByToken(String token);
    Page<PhotoResponse> listPhotosByToken(String token, Pageable pageable);
    void revoke(String token, Long requesterId, boolean isAdmin);
    /** @return id của event vừa join, để client biết điều hướng đi đâu sau khi join. */
    Long joinByToken(String token, Long userId);
}
