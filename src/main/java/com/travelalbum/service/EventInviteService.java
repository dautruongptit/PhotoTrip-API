package com.travelalbum.service;

import com.travelalbum.dto.response.EventInviteResponse;
import com.travelalbum.enums.EventMemberRole;

import java.util.List;

public interface EventInviteService {
    EventInviteResponse invite(Long eventId, String email, EventMemberRole role, Long inviterId);
    List<EventInviteResponse> listMine(Long userId);
    void accept(Long inviteId, Long userId);
    void decline(Long inviteId, Long userId);
}
