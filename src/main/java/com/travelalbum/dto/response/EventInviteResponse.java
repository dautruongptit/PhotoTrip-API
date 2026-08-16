package com.travelalbum.dto.response;

import com.travelalbum.enums.EventMemberRole;
import com.travelalbum.enums.InviteStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class EventInviteResponse {
    private Long id;
    private Long eventId;
    private String eventName;
    private EventMemberRole role;
    private InviteStatus status;
    private LocalDateTime createdAt;
}
