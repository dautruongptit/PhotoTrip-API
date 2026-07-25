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
        return photoRepository.findById(photoId)
            .map(p -> p.getEvent().getOwnerId().equals(principal.getId()))
            .orElse(false);
    }
}
