package com.travelalbum.repository;

import com.travelalbum.entity.ShareLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShareLinkRepository extends JpaRepository<ShareLink, Long> {
    Optional<ShareLink> findByTokenAndActiveTrue(String token);
    List<ShareLink> findByEventId(Long eventId);
}
