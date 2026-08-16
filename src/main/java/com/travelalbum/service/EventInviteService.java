package com.travelalbum.service;

import com.travelalbum.dto.response.EventInviteResponse;
import com.travelalbum.enums.EventMemberRole;

import java.util.List;

public interface EventInviteService {
    EventInviteResponse invite(Long eventId, String email, EventMemberRole role, Long inviterId, boolean isAdmin);
    List<EventInviteResponse> listMine(Long userId);
    /** @return id của event vừa join, để client biết điều hướng đi đâu sau khi accept. */
    Long accept(Long inviteId, Long userId);
    void decline(Long inviteId, Long userId);
}
