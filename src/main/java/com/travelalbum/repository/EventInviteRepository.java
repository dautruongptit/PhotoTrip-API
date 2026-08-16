package com.travelalbum.repository;

import com.travelalbum.entity.EventInvite;
import com.travelalbum.enums.InviteStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EventInviteRepository extends JpaRepository<EventInvite, Long> {

    List<EventInvite> findByInvitedUserIdAndStatus(Long invitedUserId, InviteStatus status);

    Optional<EventInvite> findByEventIdAndInvitedUserId(Long eventId, Long invitedUserId);
}
