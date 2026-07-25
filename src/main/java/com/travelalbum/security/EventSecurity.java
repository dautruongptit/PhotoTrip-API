package com.travelalbum.security;

import com.travelalbum.repository.EventRepository;
import com.travelalbum.security.userdetails.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("eventSecurity")
@RequiredArgsConstructor
public class EventSecurity {

    private final EventRepository eventRepository;

    public boolean isOwner(Long eventId, Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return eventRepository.findById(eventId)
            .map(e -> e.getOwnerId().equals(principal.getId()))
            .orElse(false);
    }
}
