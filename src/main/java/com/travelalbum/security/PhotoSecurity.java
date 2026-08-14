package com.travelalbum.security;

import com.travelalbum.repository.PhotoRepository;
import com.travelalbum.security.userdetails.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("photoSecurity")
@RequiredArgsConstructor
public class PhotoSecurity {

    private final PhotoRepository photoRepository;

    public boolean isOwner(Long photoId, Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        // KHÔNG dùng findById(...).map(p -> p.getEvent().getOwnerId()...) — Photo.event
        // là quan hệ LAZY, session đã đóng ngay sau khi findById() trả về (open-in-view:
        // false) nên chạm vào field ngoài id sẽ ném LazyInitializationException.
        return photoRepository.findOwnerIdByPhotoId(photoId)
            .map(ownerId -> ownerId.equals(principal.getId()))
            .orElse(false);
    }
}
