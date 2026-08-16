# Event Membership (VIEWER/EDITOR) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a user other than the event owner become a member (VIEWER or EDITOR) of an event — via an owner-issued share link or a direct email invite — and gate upload/view endpoints on membership instead of owner-only.

**Architecture:** New `event_members` / `event_invites` tables + `EventMemberRole`/`InviteStatus` enums. `EventSecurity` gains `canView`/`canUpload` used by `@PreAuthorize` on the Event/Photo controllers. `ShareLink` gets a `role` column so links can grant VIEWER or EDITOR on join. A new `EventInviteService` handles the email-invite → accept/decline flow. Enabling non-owner uploads exposes a latent bug where photo storage/quota was resolved from the *event owner* instead of the *uploader* — fixed as part of this work (Tasks 6–7), since it would otherwise silently break downloads/deletes/quota accounting for member-uploaded photos.

**Tech Stack:** Spring Boot 3 (Java 17), Spring Security method security (`@PreAuthorize`), Spring Data JPA, MySQL + Flyway, JUnit 5 + Mockito + AssertJ (no `@SpringBootTest`/MockMvc anywhere in this repo — stick to that convention).

**Spec:** `docs/superpowers/specs/2026-08-16-event-membership-design.md`

## Global Constraints

- Java 17, Spring Boot 3, MySQL (`ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`), Flyway migrations under `src/main/resources/db/migration`, next version is `V10`.
- `spring.jpa.hibernate.ddl-auto: validate` and `open-in-view: false` — Flyway is the only source of schema truth; any lazy JPA relation (`Event`, in particular) must only be touched inside a `@Transactional` method, never from a Controller after `findById()` returns.
- Enums persist as `@Enumerated(EnumType.STRING)`, column `VARCHAR(20)`, matching `User.role`/`User.status`.
- No controller-level tests exist in this repo (no MockMvc/`@SpringBootTest`) — don't introduce that pattern here; verify controller wiring by compiling + running the full unit suite (`mvn test`).
- Commit after each task.

---

### Task 1: Foundation — migration, enums, entities, repositories

**Files:**
- Create: `src/main/resources/db/migration/V10__add_event_membership.sql`
- Create: `src/main/java/com/travelalbum/enums/EventMemberRole.java`
- Create: `src/main/java/com/travelalbum/enums/InviteStatus.java`
- Create: `src/main/java/com/travelalbum/entity/EventMember.java`
- Create: `src/main/java/com/travelalbum/entity/EventInvite.java`
- Create: `src/main/java/com/travelalbum/repository/EventMemberRepository.java`
- Create: `src/main/java/com/travelalbum/repository/EventInviteRepository.java`
- Modify: `src/main/java/com/travelalbum/entity/ShareLink.java`

**Interfaces:**
- Produces: `EventMemberRole{VIEWER,EDITOR}`, `InviteStatus{PENDING,ACCEPTED,DECLINED}`; `EventMemberRepository.existsByEventIdAndUserId(Long,Long):boolean`, `.existsByEventIdAndUserIdAndRole(Long,Long,EventMemberRole):boolean`, `.findByEventIdAndUserId(Long,Long):Optional<EventMember>`; `EventInviteRepository.findByInvitedUserIdAndStatus(Long,InviteStatus):List<EventInvite>`, `.findByEventIdAndInvitedUserId(Long,Long):Optional<EventInvite>`; `EventMember.builder()` fields `event,userId,role,invitedBy`; `EventInvite.builder()` fields `event,invitedUserId,role,status,invitedBy`; `ShareLink.getRole()/setRole(EventMemberRole)` (default `VIEWER`).

No tests in this task — it's schema + plain data classes, same convention as the rest of the repo (no existing test file touches an entity or a Spring Data repository interface directly).

- [ ] **Step 1: Write the migration**

`src/main/resources/db/migration/V10__add_event_membership.sql`:
```sql
-- ============ SHARE LINK ROLE ============
ALTER TABLE share_links ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'VIEWER';

-- ============ EVENT MEMBERS ============
CREATE TABLE event_members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    invited_by BIGINT,
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_event_member UNIQUE (event_id, user_id),
    CONSTRAINT fk_event_member_event FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE,
    CONSTRAINT fk_event_member_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_event_member_inviter FOREIGN KEY (invited_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_event_members_user ON event_members(user_id);

-- ============ EVENT INVITES ============
CREATE TABLE event_invites (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id BIGINT NOT NULL,
    invited_user_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    invited_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded_at TIMESTAMP NULL,
    CONSTRAINT uq_event_invite_pending UNIQUE (event_id, invited_user_id),
    CONSTRAINT fk_event_invite_event FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE,
    CONSTRAINT fk_event_invite_user FOREIGN KEY (invited_user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_event_invite_inviter FOREIGN KEY (invited_by) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_event_invites_user ON event_invites(invited_user_id);
```

- [ ] **Step 2: Add the enums**

`src/main/java/com/travelalbum/enums/EventMemberRole.java`:
```java
package com.travelalbum.enums;

public enum EventMemberRole {
    VIEWER,
    EDITOR
}
```

`src/main/java/com/travelalbum/enums/InviteStatus.java`:
```java
package com.travelalbum.enums;

public enum InviteStatus {
    PENDING,
    ACCEPTED,
    DECLINED
}
```

- [ ] **Step 3: Add the `EventMember` entity**

`src/main/java/com/travelalbum/entity/EventMember.java`:
```java
package com.travelalbum.entity;

import com.travelalbum.enums.EventMemberRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "event_members",
    uniqueConstraints = @UniqueConstraint(name = "uq_event_member", columnNames = {"event_id", "user_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventMemberRole role;

    @Column(name = "invited_by")
    private Long invitedBy;

    @CreationTimestamp
    @Column(name = "joined_at", updatable = false)
    private LocalDateTime joinedAt;
}
```

- [ ] **Step 4: Add the `EventInvite` entity**

`src/main/java/com/travelalbum/entity/EventInvite.java`:
```java
package com.travelalbum.entity;

import com.travelalbum.enums.EventMemberRole;
import com.travelalbum.enums.InviteStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "event_invites",
    uniqueConstraints = @UniqueConstraint(name = "uq_event_invite_pending", columnNames = {"event_id", "invited_user_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "invited_user_id", nullable = false)
    private Long invitedUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventMemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private InviteStatus status = InviteStatus.PENDING;

    @Column(name = "invited_by", nullable = false)
    private Long invitedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;
}
```

- [ ] **Step 5: Add the repositories**

`src/main/java/com/travelalbum/repository/EventMemberRepository.java`:
```java
package com.travelalbum.repository;

import com.travelalbum.entity.EventMember;
import com.travelalbum.enums.EventMemberRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EventMemberRepository extends JpaRepository<EventMember, Long> {

    boolean existsByEventIdAndUserId(Long eventId, Long userId);

    boolean existsByEventIdAndUserIdAndRole(Long eventId, Long userId, EventMemberRole role);

    Optional<EventMember> findByEventIdAndUserId(Long eventId, Long userId);
}
```

`src/main/java/com/travelalbum/repository/EventInviteRepository.java`:
```java
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
```

- [ ] **Step 6: Add `role` to `ShareLink`**

In `src/main/java/com/travelalbum/entity/ShareLink.java`, add the import and field (default `VIEWER`, matching the migration's `DEFAULT 'VIEWER'`):
```java
import com.travelalbum.enums.EventMemberRole;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
```
```java
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EventMemberRole role = EventMemberRole.VIEWER;
```
(placed after the `active` field, before `createdAt`)

- [ ] **Step 7: Compile**

Run: `mvn -q compile`
Expected: `BUILD SUCCESS`, no errors.

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/db/migration/V10__add_event_membership.sql \
        src/main/java/com/travelalbum/enums/EventMemberRole.java \
        src/main/java/com/travelalbum/enums/InviteStatus.java \
        src/main/java/com/travelalbum/entity/EventMember.java \
        src/main/java/com/travelalbum/entity/EventInvite.java \
        src/main/java/com/travelalbum/repository/EventMemberRepository.java \
        src/main/java/com/travelalbum/repository/EventInviteRepository.java \
        src/main/java/com/travelalbum/entity/ShareLink.java
git commit -m "feat: add event_members/event_invites schema and entities"
```

---

### Task 2: `EventSecurity.canView` / `canUpload`

**Files:**
- Modify: `src/main/java/com/travelalbum/security/EventSecurity.java`
- Test: `src/test/java/com/travelalbum/security/EventSecurityTest.java` (new)

**Interfaces:**
- Consumes: `EventMemberRepository` (Task 1), `EventRepository.findById(Long):Optional<Event>` (existing).
- Produces: `EventSecurity.canView(Long eventId, Authentication auth):boolean`, `EventSecurity.canUpload(Long eventId, Authentication auth):boolean` — used by `@PreAuthorize` in Tasks 3/6.

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/travelalbum/security/EventSecurityTest.java`:
```java
package com.travelalbum.security;

import com.travelalbum.entity.Event;
import com.travelalbum.enums.EventMemberRole;
import com.travelalbum.enums.Role;
import com.travelalbum.repository.EventMemberRepository;
import com.travelalbum.repository.EventRepository;
import com.travelalbum.security.userdetails.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventSecurityTest {

    @Mock private EventRepository eventRepository;
    @Mock private EventMemberRepository eventMemberRepository;
    @Mock private Authentication authentication;

    @InjectMocks
    private EventSecurity eventSecurity;

    private void asUser(long userId) {
        lenient().when(authentication.getPrincipal())
                .thenReturn(new UserPrincipal(userId, "u" + userId + "@test.com", Role.USER));
    }

    @Test
    void canView_true_whenOwner() {
        asUser(1L);
        when(eventRepository.findById(10L)).thenReturn(Optional.of(Event.builder().id(10L).ownerId(1L).build()));

        assertThat(eventSecurity.canView(10L, authentication)).isTrue();
    }

    @Test
    void canView_true_whenMember() {
        asUser(2L);
        when(eventRepository.findById(10L)).thenReturn(Optional.of(Event.builder().id(10L).ownerId(1L).build()));
        when(eventMemberRepository.existsByEventIdAndUserId(10L, 2L)).thenReturn(true);

        assertThat(eventSecurity.canView(10L, authentication)).isTrue();
    }

    @Test
    void canView_false_whenNeitherOwnerNorMember() {
        asUser(3L);
        when(eventRepository.findById(10L)).thenReturn(Optional.of(Event.builder().id(10L).ownerId(1L).build()));
        when(eventMemberRepository.existsByEventIdAndUserId(10L, 3L)).thenReturn(false);

        assertThat(eventSecurity.canView(10L, authentication)).isFalse();
    }

    @Test
    void canUpload_true_whenOwner() {
        asUser(1L);
        when(eventRepository.findById(10L)).thenReturn(Optional.of(Event.builder().id(10L).ownerId(1L).build()));

        assertThat(eventSecurity.canUpload(10L, authentication)).isTrue();
    }

    @Test
    void canUpload_true_whenEditorMember() {
        asUser(2L);
        when(eventRepository.findById(10L)).thenReturn(Optional.of(Event.builder().id(10L).ownerId(1L).build()));
        when(eventMemberRepository.existsByEventIdAndUserIdAndRole(10L, 2L, EventMemberRole.EDITOR)).thenReturn(true);

        assertThat(eventSecurity.canUpload(10L, authentication)).isTrue();
    }

    @Test
    void canUpload_false_whenViewerMember() {
        asUser(2L);
        when(eventRepository.findById(10L)).thenReturn(Optional.of(Event.builder().id(10L).ownerId(1L).build()));
        when(eventMemberRepository.existsByEventIdAndUserIdAndRole(10L, 2L, EventMemberRole.EDITOR)).thenReturn(false);

        assertThat(eventSecurity.canUpload(10L, authentication)).isFalse();
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -q test -Dtest=EventSecurityTest`
Expected: compile error (`canView`/`canUpload` don't exist yet) or FAIL.

- [ ] **Step 3: Implement**

Replace the full content of `src/main/java/com/travelalbum/security/EventSecurity.java`:
```java
package com.travelalbum.security;

import com.travelalbum.enums.EventMemberRole;
import com.travelalbum.repository.EventMemberRepository;
import com.travelalbum.repository.EventRepository;
import com.travelalbum.security.userdetails.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("eventSecurity")
@RequiredArgsConstructor
public class EventSecurity {

    private final EventRepository eventRepository;
    private final EventMemberRepository eventMemberRepository;

    public boolean isOwner(Long eventId, Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return eventRepository.findById(eventId)
            .map(e -> e.getOwnerId().equals(principal.getId()))
            .orElse(false);
    }

    /** Owner hoặc bất kỳ member nào (VIEWER/EDITOR) đều xem được event/danh sách ảnh. */
    public boolean canView(Long eventId, Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return isOwner(eventId, authentication)
            || eventMemberRepository.existsByEventIdAndUserId(eventId, principal.getId());
    }

    /** Owner hoặc member role EDITOR mới upload được. */
    public boolean canUpload(Long eventId, Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return isOwner(eventId, authentication)
            || eventMemberRepository.existsByEventIdAndUserIdAndRole(eventId, principal.getId(), EventMemberRole.EDITOR);
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `mvn -q test -Dtest=EventSecurityTest`
Expected: PASS, 6 tests green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/travelalbum/security/EventSecurity.java src/test/java/com/travelalbum/security/EventSecurityTest.java
git commit -m "feat: add EventSecurity.canView/canUpload for member access checks"
```

---

### Task 3: Open `GET /api/events/{id}` and `GET /api/events/{id}/photos` to members

**Files:**
- Modify: `src/main/java/com/travelalbum/controller/EventController.java:58`
- Modify: `src/main/java/com/travelalbum/controller/PhotoController.java:60`

**Interfaces:**
- Consumes: `EventSecurity.canView` (Task 2).

No new tests — this repo has no controller-level test harness; verified by compiling and by Task 2's `EventSecurityTest` already covering the underlying logic.

- [ ] **Step 1: Update `EventController.getById`**

In `EventController.java`, change:
```java
    @PreAuthorize("hasRole('ADMIN') or @eventSecurity.isOwner(#id, authentication)")
    public ApiResponse<EventResponse> getById(@PathVariable Long id) {
```
to:
```java
    @PreAuthorize("hasRole('ADMIN') or @eventSecurity.canView(#id, authentication)")
    public ApiResponse<EventResponse> getById(@PathVariable Long id) {
```

- [ ] **Step 2: Update `PhotoController.listByEvent`**

In `PhotoController.java`, change:
```java
    @PreAuthorize("hasRole('ADMIN') or @eventSecurity.isOwner(#eventId, authentication)")
    public ApiResponse<Page<PhotoResponse>> listByEvent(@PathVariable Long eventId, Pageable pageable) {
```
to:
```java
    @PreAuthorize("hasRole('ADMIN') or @eventSecurity.canView(#eventId, authentication)")
    public ApiResponse<Page<PhotoResponse>> listByEvent(@PathVariable Long eventId, Pageable pageable) {
```

- [ ] **Step 3: Compile + run full suite**

Run: `mvn -q test`
Expected: `BUILD SUCCESS`, all existing tests still pass (this is a pure `@PreAuthorize` expression swap, no behavior asserted by unit tests changes).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/travelalbum/controller/EventController.java src/main/java/com/travelalbum/controller/PhotoController.java
git commit -m "feat: allow event members to view event and list its photos"
```

---

### Task 4: `ShareService` — share links carry a role, add `joinByToken`

**Files:**
- Modify: `src/main/java/com/travelalbum/service/ShareService.java`
- Modify: `src/main/java/com/travelalbum/service/impl/ShareServiceImpl.java`
- Modify: `src/main/java/com/travelalbum/dto/response/ShareLinkResponse.java`
- Modify: `src/test/java/com/travelalbum/service/impl/ShareServiceImplTest.java`

**Interfaces:**
- Consumes: `EventMemberRepository` (Task 1), `EventMember.builder()` (Task 1).
- Produces: `ShareService.create(Long eventId, Long userId, EventMemberRole role):ShareLinkResponse` (signature change — `role` added), `ShareService.joinByToken(String token, Long userId):void` — consumed by `ShareController` in Task 5.

- [ ] **Step 1: Update the existing tests for the new `create` signature and write the failing `joinByToken` tests**

Replace `src/test/java/com/travelalbum/service/impl/ShareServiceImplTest.java` in full:
```java
package com.travelalbum.service.impl;

import com.travelalbum.dto.response.EventResponse;
import com.travelalbum.dto.response.ShareLinkResponse;
import com.travelalbum.entity.Event;
import com.travelalbum.entity.EventMember;
import com.travelalbum.entity.ShareLink;
import com.travelalbum.enums.EventMemberRole;
import com.travelalbum.exception.NotFoundException;
import com.travelalbum.mapper.EventMapper;
import com.travelalbum.mapper.PhotoMapper;
import com.travelalbum.repository.EventMemberRepository;
import com.travelalbum.repository.EventRepository;
import com.travelalbum.repository.PhotoRepository;
import com.travelalbum.repository.ShareLinkRepository;
import com.travelalbum.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShareServiceImplTest {

    @Mock private ShareLinkRepository shareLinkRepository;
    @Mock private EventRepository eventRepository;
    @Mock private PhotoRepository photoRepository;
    @Mock private EventMemberRepository eventMemberRepository;
    @Mock private EventMapper eventMapper;
    @Mock private PhotoMapper photoMapper;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private ShareServiceImpl shareService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(shareService, "frontendUrl", "http://triptravel.example.com");
    }

    @Test
    void create_throwsAccessDenied_whenRequesterIsNotOwner() {
        Event event = Event.builder().id(1L).ownerId(1L).build();
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> shareService.create(1L, 2L, EventMemberRole.VIEWER))
                .isInstanceOf(AccessDeniedException.class);
        verify(shareLinkRepository, never()).save(any());
    }

    @Test
    void create_buildsShareUrlFromFrontendUrl_whenOwner() {
        Event event = Event.builder().id(1L).ownerId(1L).build();
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(shareLinkRepository.findByTokenAndActiveTrue(anyString())).thenReturn(Optional.empty());

        ShareLinkResponse response = shareService.create(1L, 1L, EventMemberRole.VIEWER);

        assertThat(response.isActive()).isTrue();
        assertThat(response.getToken()).isNotBlank();
        assertThat(response.getShareUrl()).isEqualTo("http://triptravel.example.com/share/" + response.getToken());
        assertThat(response.getRole()).isEqualTo(EventMemberRole.VIEWER);
        verify(shareLinkRepository).save(any(ShareLink.class));
        verify(auditLogService).log(eq(1L), eq("SHARE_EVENT"), eq("EVENT"), eq(1L), any(), any(), eq("SUCCESS"));
    }

    @Test
    void create_defaultsToViewerRole_whenRoleNotProvided() {
        Event event = Event.builder().id(1L).ownerId(1L).build();
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(shareLinkRepository.findByTokenAndActiveTrue(anyString())).thenReturn(Optional.empty());

        ShareLinkResponse response = shareService.create(1L, 1L, null);

        assertThat(response.getRole()).isEqualTo(EventMemberRole.VIEWER);
    }

    @Test
    void getEventByToken_throwsNotFound_whenTokenUnknown() {
        when(shareLinkRepository.findByTokenAndActiveTrue("bad-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shareService.getEventByToken("bad-token")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void getEventByToken_throwsNotFound_whenLinkExpired() {
        Event event = Event.builder().id(1L).build();
        ShareLink expired = ShareLink.builder()
                .token("tok").event(event).active(true)
                .expiredAt(LocalDateTime.now().minusDays(1))
                .build();
        when(shareLinkRepository.findByTokenAndActiveTrue("tok")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> shareService.getEventByToken("tok")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void getEventByToken_returnsEvent_whenActiveAndNotExpired() {
        Event event = Event.builder().id(1L).name("Da Lat").build();
        ShareLink link = ShareLink.builder().token("tok").event(event).active(true).expiredAt(null).build();
        when(shareLinkRepository.findByTokenAndActiveTrue("tok")).thenReturn(Optional.of(link));
        when(eventMapper.toResponse(event)).thenReturn(EventResponse.builder().id(1L).name("Da Lat").build());

        EventResponse response = shareService.getEventByToken("tok");

        assertThat(response.getId()).isEqualTo(1L);
    }

    @Test
    void revoke_throwsAccessDenied_whenNotOwnerAndNotAdmin() {
        Event event = Event.builder().id(1L).ownerId(1L).build();
        ShareLink link = ShareLink.builder().token("tok").event(event).active(true).build();
        when(shareLinkRepository.findByTokenAndActiveTrue("tok")).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> shareService.revoke("tok", 2L, false)).isInstanceOf(AccessDeniedException.class);
        assertThat(link.isActive()).isTrue();
    }

    @Test
    void revoke_deactivatesLink_whenOwner() {
        Event event = Event.builder().id(1L).ownerId(1L).build();
        ShareLink link = ShareLink.builder().token("tok").event(event).active(true).build();
        when(shareLinkRepository.findByTokenAndActiveTrue("tok")).thenReturn(Optional.of(link));

        shareService.revoke("tok", 1L, false);

        assertThat(link.isActive()).isFalse();
        verify(shareLinkRepository).save(link);
        verify(auditLogService).log(eq(1L), eq("REVOKE_SHARE"), eq("EVENT"), eq(1L), any(), any(), eq("SUCCESS"));
    }

    @Test
    void revoke_succeeds_whenAdminButNotOwner() {
        Event event = Event.builder().id(1L).ownerId(1L).build();
        ShareLink link = ShareLink.builder().token("tok").event(event).active(true).build();
        when(shareLinkRepository.findByTokenAndActiveTrue("tok")).thenReturn(Optional.of(link));

        shareService.revoke("tok", 999L, true);

        assertThat(link.isActive()).isFalse();
    }

    @Test
    void joinByToken_throwsNotFound_whenTokenUnknown() {
        when(shareLinkRepository.findByTokenAndActiveTrue("bad-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shareService.joinByToken("bad-token", 2L)).isInstanceOf(NotFoundException.class);
        verify(eventMemberRepository, never()).save(any());
    }

    @Test
    void joinByToken_createsMemberWithLinkRole() {
        Event event = Event.builder().id(1L).ownerId(1L).build();
        ShareLink link = ShareLink.builder().token("tok").event(event).active(true)
                .role(EventMemberRole.EDITOR).createdBy(1L).build();
        when(shareLinkRepository.findByTokenAndActiveTrue("tok")).thenReturn(Optional.of(link));
        when(eventMemberRepository.existsByEventIdAndUserId(1L, 2L)).thenReturn(false);

        shareService.joinByToken("tok", 2L);

        verify(eventMemberRepository).save(argThat((EventMember m) ->
                m.getUserId().equals(2L) && m.getRole() == EventMemberRole.EDITOR && m.getInvitedBy().equals(1L)));
        verify(auditLogService).log(eq(2L), eq("JOIN_EVENT"), eq("EVENT"), eq(1L), any(), any(), eq("SUCCESS"));
    }

    @Test
    void joinByToken_isIdempotent_whenAlreadyMember() {
        Event event = Event.builder().id(1L).ownerId(1L).build();
        ShareLink link = ShareLink.builder().token("tok").event(event).active(true)
                .role(EventMemberRole.VIEWER).build();
        when(shareLinkRepository.findByTokenAndActiveTrue("tok")).thenReturn(Optional.of(link));
        when(eventMemberRepository.existsByEventIdAndUserId(1L, 2L)).thenReturn(true);

        shareService.joinByToken("tok", 2L);

        verify(eventMemberRepository, never()).save(any());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any(), any(), any());
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -q test -Dtest=ShareServiceImplTest`
Expected: compile error (`create` still takes 2 args, `joinByToken` doesn't exist).

- [ ] **Step 3: Update `ShareLinkResponse`**

Add to `src/main/java/com/travelalbum/dto/response/ShareLinkResponse.java`:
```java
import com.travelalbum.enums.EventMemberRole;
```
```java
    private EventMemberRole role;
```
(as a new field alongside `token`/`shareUrl`/`expiredAt`/`active`)

- [ ] **Step 4: Update `ShareService` interface**

Replace `src/main/java/com/travelalbum/service/ShareService.java`:
```java
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
    void joinByToken(String token, Long userId);
}
```

- [ ] **Step 5: Implement in `ShareServiceImpl`**

In `src/main/java/com/travelalbum/service/impl/ShareServiceImpl.java`:

Add imports:
```java
import com.travelalbum.entity.EventMember;
import com.travelalbum.enums.EventMemberRole;
import com.travelalbum.repository.EventMemberRepository;
```

Add field + constructor param (it's `@RequiredArgsConstructor`, just add the field):
```java
    private final EventMemberRepository eventMemberRepository;
```

Replace the `create` method:
```java
    @Override
    @Transactional
    public ShareLinkResponse create(Long eventId, Long userId, EventMemberRole role) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new NotFoundException("Event not found"));
        if (!event.getOwnerId().equals(userId)) {
            throw new AccessDeniedException("Not the owner of this event");
        }
        EventMemberRole linkRole = role != null ? role : EventMemberRole.VIEWER;
        String token = generateUniqueToken();
        ShareLink link = ShareLink.builder()
            .event(event)
            .token(token)
            .createdBy(userId)
            .active(true)
            .role(linkRole)
            .build();
        shareLinkRepository.save(link);
        auditLogService.log(userId, "SHARE_EVENT", "EVENT", eventId, null, null, "SUCCESS");

        return ShareLinkResponse.builder()
            .token(token)
            .shareUrl(frontendUrl + "/share/" + token)
            .active(true)
            .role(linkRole)
            .build();
    }
```

Add the `joinByToken` method (after `revoke`, before `generateUniqueToken`):
```java
    @Override
    @Transactional
    public void joinByToken(String token, Long userId) {
        ShareLink link = shareLinkRepository.findByTokenAndActiveTrue(token)
            .filter(l -> l.getExpiredAt() == null || l.getExpiredAt().isAfter(LocalDateTime.now()))
            .orElseThrow(() -> new NotFoundException("Share link not found or expired"));
        Long eventId = link.getEvent().getId();
        if (eventMemberRepository.existsByEventIdAndUserId(eventId, userId)) {
            return;
        }
        EventMember member = EventMember.builder()
            .event(link.getEvent())
            .userId(userId)
            .role(link.getRole())
            .invitedBy(link.getCreatedBy())
            .build();
        eventMemberRepository.save(member);
        auditLogService.log(userId, "JOIN_EVENT", "EVENT", eventId, null, null, "SUCCESS");
    }
```

- [ ] **Step 6: Run to verify it passes**

Run: `mvn -q test -Dtest=ShareServiceImplTest`
Expected: PASS, all tests green.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/travelalbum/service/ShareService.java \
        src/main/java/com/travelalbum/service/impl/ShareServiceImpl.java \
        src/main/java/com/travelalbum/dto/response/ShareLinkResponse.java \
        src/test/java/com/travelalbum/service/impl/ShareServiceImplTest.java
git commit -m "feat: share links carry a role; add ShareService.joinByToken"
```

---

### Task 5: `ShareController` — role param on create, new join endpoint

**Files:**
- Modify: `src/main/java/com/travelalbum/controller/ShareController.java`

**Interfaces:**
- Consumes: `ShareService.create(Long,Long,EventMemberRole)`, `ShareService.joinByToken(String,Long)` (Task 4).

No new tests (no controller test harness in this repo); verified by `mvn test` (full suite) + manual smoke check in Task 10.

- [ ] **Step 1: Update the controller**

Replace `src/main/java/com/travelalbum/controller/ShareController.java` in full:
```java
package com.travelalbum.controller;

import com.travelalbum.common.ApiResponse;
import com.travelalbum.dto.response.EventResponse;
import com.travelalbum.dto.response.PhotoResponse;
import com.travelalbum.dto.response.ShareLinkResponse;
import com.travelalbum.enums.EventMemberRole;
import com.travelalbum.security.userdetails.UserPrincipal;
import com.travelalbum.service.ShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;

    @PostMapping("/api/events/{id}/share")
    @PreAuthorize("hasRole('ADMIN') or @eventSecurity.isOwner(#id, authentication)")
    public ApiResponse<ShareLinkResponse> create(@PathVariable Long id,
                                                  @RequestParam(value = "role", required = false) EventMemberRole role,
                                                  @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success("Share link created", shareService.create(id, principal.getId(), role));
    }

    @GetMapping("/api/share/{token}")
    public ApiResponse<EventResponse> viewByToken(@PathVariable String token) {
        return ApiResponse.success("OK", shareService.getEventByToken(token));
    }

    @GetMapping("/api/share/{token}/photos")
    public ApiResponse<Page<PhotoResponse>> listByToken(@PathVariable String token, Pageable pageable) {
        return ApiResponse.success("OK", shareService.listPhotosByToken(token, pageable));
    }

    @PostMapping("/api/share/{token}/join")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<Void> join(@PathVariable String token, @AuthenticationPrincipal UserPrincipal principal) {
        shareService.joinByToken(token, principal.getId());
        return ApiResponse.success("Joined event", null);
    }

    @DeleteMapping("/api/share/{token}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<Void> revoke(@PathVariable String token, @AuthenticationPrincipal UserPrincipal principal) {
        shareService.revoke(token, principal.getId(), principal.isAdmin());
        return ApiResponse.success("Share link revoked", null);
    }
}
```

Note: `/api/share/**` is already `permitAll()` at the HTTP filter level in `SecurityConfig` (needed for the two anonymous GETs), but `join`'s `@PreAuthorize("hasAnyRole('USER','ADMIN')")` still enforces authentication at the method-security layer — exactly the same pattern already used for `revoke` on the same path prefix, so no `SecurityConfig` change is needed.

- [ ] **Step 2: Compile + run full suite**

Run: `mvn -q test`
Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/travelalbum/controller/ShareController.java
git commit -m "feat: add role param to share-link creation and a join endpoint"
```

---

### Task 6: `PhotoServiceImpl.uploadMultiple` — allow owner or EDITOR member

**Files:**
- Modify: `src/main/java/com/travelalbum/service/impl/PhotoServiceImpl.java:52-57`
- Modify: `src/main/java/com/travelalbum/controller/PhotoController.java:50-57`
- Modify: `src/test/java/com/travelalbum/service/impl/PhotoServiceImplTest.java`

**Interfaces:**
- Consumes: `EventMemberRepository.existsByEventIdAndUserIdAndRole` (Task 1), `EventSecurity.canUpload` (Task 2).

- [ ] **Step 1: Write the failing test**

In `src/test/java/com/travelalbum/service/impl/PhotoServiceImplTest.java`, add the import:
```java
import com.travelalbum.repository.EventMemberRepository;
import com.travelalbum.enums.EventMemberRole;
```
add the mock field next to the others:
```java
    @Mock private EventMemberRepository eventMemberRepository;
```
and add this test (existing `uploadMultiple_throwsAccessDenied_whenRequesterNotEventOwner` stays as-is — with `eventMemberRepository` unstubbed, Mockito's default `false` return keeps it passing since requester 2L still isn't a member):
```java
    @Test
    void uploadMultiple_allowsEditorMember_evenIfNotOwner() {
        Event event = ownedEvent();
        User editor = User.builder().id(2L).storageFolder("editor_000002")
                .storageUsed(0L).storageQuota(10_000_000L).build();
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
        when(eventMemberRepository.existsByEventIdAndUserIdAndRole(10L, 2L, EventMemberRole.EDITOR)).thenReturn(true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(editor));
        when(photoRepository.existsByEventIdAndOriginalName(10L, "IMG_001.jpg")).thenReturn(false);
        when(storageService.store(eq("editor_000002"), eq("ev-folder"), any()))
                .thenReturn(new StoredFile("uuid_IMG_001.jpg", "ev-folder/uuid_IMG_001.jpg", "checksum123", 1920, 1080));
        when(photoRepository.save(any(Photo.class))).thenAnswer(inv -> {
            Photo p = inv.getArgument(0);
            p.setId(501L);
            return p;
        });
        when(photoMapper.toResponse(any(Photo.class))).thenReturn(PhotoResponse.builder().id(501L).build());

        MultipartFile[] files = { new MockMultipartFile("files", "IMG_001.jpg", "image/jpeg", "x".getBytes()) };

        UploadResultResponse result = photoService.uploadMultiple(10L, files, 2L);

        assertThat(result.getUploaded()).isEqualTo(1);
        assertThat(editor.getStorageUsed()).isEqualTo(1L);
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -q test -Dtest=PhotoServiceImplTest#uploadMultiple_allowsEditorMember_evenIfNotOwner`
Expected: FAIL with `AccessDeniedException` (current code still rejects non-owners unconditionally).

- [ ] **Step 3: Implement**

In `src/main/java/com/travelalbum/service/impl/PhotoServiceImpl.java`, add imports:
```java
import com.travelalbum.enums.EventMemberRole;
import com.travelalbum.repository.EventMemberRepository;
```
add the field:
```java
    private final EventMemberRepository eventMemberRepository;
```
replace:
```java
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found"));
        if (!event.getOwnerId().equals(userId)) {
            throw new AccessDeniedException("Not the owner of this event");
        }
```
with:
```java
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found"));
        boolean isOwner = event.getOwnerId().equals(userId);
        boolean isEditor = eventMemberRepository.existsByEventIdAndUserIdAndRole(eventId, userId, EventMemberRole.EDITOR);
        if (!isOwner && !isEditor) {
            throw new AccessDeniedException("Not allowed to upload to this event");
        }
```

- [ ] **Step 4: Run to verify it passes**

Run: `mvn -q test -Dtest=PhotoServiceImplTest`
Expected: PASS, all tests green (including the pre-existing `uploadMultiple_throwsAccessDenied_whenRequesterNotEventOwner`).

- [ ] **Step 5: Wire the controller's `@PreAuthorize`**

In `src/main/java/com/travelalbum/controller/PhotoController.java`, change:
```java
    @Auditable(action = "UPLOAD", targetType = "EVENT")
    @PostMapping(value = "/api/events/{eventId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<UploadResultResponse> upload(@PathVariable Long eventId,
```
to:
```java
    @Auditable(action = "UPLOAD", targetType = "EVENT")
    @PostMapping(value = "/api/events/{eventId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN') or @eventSecurity.canUpload(#eventId, authentication)")
    public ApiResponse<UploadResultResponse> upload(@PathVariable Long eventId,
```

- [ ] **Step 6: Compile + run full suite**

Run: `mvn -q test`
Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/travelalbum/service/impl/PhotoServiceImpl.java \
        src/main/java/com/travelalbum/controller/PhotoController.java \
        src/test/java/com/travelalbum/service/impl/PhotoServiceImplTest.java
git commit -m "feat: allow EDITOR members to upload photos to a shared event"
```

---

### Task 7: Fix storage/quota to key off the photo's uploader, not the event owner

**Why:** `uploadMultiple` already stores files under the *uploading user's* `storageFolder` and counts the upload against *their* quota (`storageService.store(user.getStorageFolder(), ...)`, `user.setStorageUsed(...)`). Until now `user` was always the event owner, so `PhotoServiceImpl.delete` and `PhotoController`'s download/zip endpoints could get away with resolving the storage folder from `event.getOwnerId()`. Once EDITOR members upload (Task 6), a member's photo physically lives under *their* folder — deleting/downloading it via the owner's folder will fail to find the file (or silently touch the wrong owner's quota). This task keys those three code paths off `photo.getUploadedBy()`, falling back to the event owner only for legacy photos where `uploadedBy` is `null` (uploader account deleted, `ON DELETE SET NULL`).

**Files:**
- Modify: `src/main/java/com/travelalbum/service/impl/PhotoServiceImpl.java` (`delete` method)
- Modify: `src/main/java/com/travelalbum/controller/PhotoController.java:116-125` (`resolveOwnerStorageFolder`)
- Modify: `src/test/java/com/travelalbum/service/impl/PhotoServiceImplTest.java`

**Interfaces:**
- Consumes: `Photo.getUploadedBy():Long` (existing column), `PhotoRepository.findOwnerIdByPhotoId` (existing, used only as legacy fallback).

- [ ] **Step 1: Write the failing tests**

Add to `src/test/java/com/travelalbum/service/impl/PhotoServiceImplTest.java`:
```java
    @Test
    void delete_allowsUploaderToDeleteOwnPhoto_evenIfNotEventOwner() {
        Event event = ownedEvent();
        event.setPhotoCount(1);
        event.setTotalSize(1000L);
        Photo photo = Photo.builder().id(500L).event(event).path("editor-folder/x.jpg").size(1000L).uploadedBy(2L).build();
        User uploader = User.builder().id(2L).storageFolder("editor-folder").storageUsed(1000L).storageQuota(10_000_000L).build();

        when(photoRepository.findById(500L)).thenReturn(Optional.of(photo));
        when(userRepository.findById(2L)).thenReturn(Optional.of(uploader));

        photoService.delete(500L, 2L, false);

        assertThat(event.getPhotoCount()).isEqualTo(0);
        assertThat(uploader.getStorageUsed()).isEqualTo(0L);
        verify(storageService).delete("editor-folder", "editor-folder/x.jpg");
        verify(photoRepository).delete(photo);
    }

    @Test
    void delete_deniesMemberWhoDidNotUploadTheirPhoto() {
        Event event = ownedEvent();
        Photo photo = Photo.builder().id(500L).event(event).path("ev-folder/x.jpg").size(1000L).uploadedBy(2L).build();
        when(photoRepository.findById(500L)).thenReturn(Optional.of(photo));

        assertThatThrownBy(() -> photoService.delete(500L, 3L, false))
                .isInstanceOf(AccessDeniedException.class);
        verify(storageService, never()).delete(anyString(), anyString());
    }
```

- [ ] **Step 2: Run to verify they fail**

Run: `mvn -q test -Dtest=PhotoServiceImplTest#delete_allowsUploaderToDeleteOwnPhoto_evenIfNotEventOwner+delete_deniesMemberWhoDidNotUploadTheirPhoto`
Expected: first FAILs with `AccessDeniedException` (current code rejects any non-owner); second currently passes already (harmless — confirms no regression once Step 3 lands).

- [ ] **Step 3: Implement in `PhotoServiceImpl.delete`**

Replace the `delete` method body in `src/main/java/com/travelalbum/service/impl/PhotoServiceImpl.java`:
```java
    @Override
    @Transactional
    public void delete(Long photoId, Long requesterId, boolean isAdmin) {
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new NotFoundException("Photo not found"));
        Long eventOwnerId = photo.getEvent().getOwnerId();
        boolean isEventOwner = eventOwnerId.equals(requesterId);
        boolean isUploader = requesterId.equals(photo.getUploadedBy());
        if (!isAdmin && !isEventOwner && !isUploader) {
            throw new AccessDeniedException("Not allowed to delete this photo");
        }

        // Ảnh vật lý nằm trong storage folder của NGƯỜI UPLOAD (xem uploadMultiple), không
        // phải owner của event — chỉ fallback về owner khi ảnh cũ không còn uploadedBy
        // (tài khoản gốc đã bị xoá, FK ON DELETE SET NULL).
        Long fileOwnerUserId = photo.getUploadedBy() != null ? photo.getUploadedBy() : eventOwnerId;
        User fileOwner = userRepository.findById(fileOwnerUserId)
                .orElseThrow(() -> new NotFoundException("Owner not found"));
        storageService.delete(fileOwner.getStorageFolder(), photo.getPath());

        Event event = photo.getEvent();
        event.setPhotoCount(Math.max(0, event.getPhotoCount() - 1));
        event.setTotalSize(Math.max(0, event.getTotalSize() - photo.getSize()));
        eventRepository.save(event);

        fileOwner.setStorageUsed(Math.max(0, fileOwner.getStorageUsed() - photo.getSize()));
        userRepository.save(fileOwner);

        photoRepository.delete(photo);
        auditLogService.log(requesterId, "DELETE_PHOTO", "PHOTO", photoId, null, null, "SUCCESS");
    }
```

- [ ] **Step 4: Run to verify it passes**

Run: `mvn -q test -Dtest=PhotoServiceImplTest`
Expected: PASS, all tests green — including the pre-existing `delete_throwsAccessDenied_whenNotOwnerAndNotAdmin` and `delete_decrementsCountersAndDeletesFile_whenOwner`, both unaffected since their photos have no `uploadedBy` set (`null` falls back to `eventOwnerId`, matching prior behavior).

- [ ] **Step 5: Fix `PhotoController`'s storage-folder resolution**

In `src/main/java/com/travelalbum/controller/PhotoController.java`, rename and fix `resolveOwnerStorageFolder`:
```java
    private String resolveStorageFolder(Photo photo) {
        // Ảnh nằm trong storage folder của người upload (xem PhotoServiceImpl.uploadMultiple),
        // không phải owner của event. photo.getUploadedBy() là cột thường (không LAZY) nên đọc
        // trực tiếp an toàn; chỉ fallback sang owner qua join JPQL khi ảnh cũ không có uploader
        // (tài khoản gốc đã bị xoá, FK ON DELETE SET NULL).
        Long fileOwnerUserId = photo.getUploadedBy() != null
                ? photo.getUploadedBy()
                : photoRepository.findOwnerIdByPhotoId(photo.getId())
                        .orElseThrow(() -> new NotFoundException("Event not found"));
        User user = userRepository.findById(fileOwnerUserId)
                .orElseThrow(() -> new NotFoundException("Owner not found"));
        return user.getStorageFolder();
    }
```
and update its two call sites (`download` and `downloadZip`) from `resolveOwnerStorageFolder(photo)` to `resolveStorageFolder(photo)`.

- [ ] **Step 6: Compile + run full suite**

Run: `mvn -q test`
Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/travelalbum/service/impl/PhotoServiceImpl.java \
        src/main/java/com/travelalbum/controller/PhotoController.java \
        src/test/java/com/travelalbum/service/impl/PhotoServiceImplTest.java
git commit -m "fix: resolve photo storage/quota by uploader instead of event owner"
```

---

### Task 8: `EventInviteService` — invite by email, accept/decline

**Files:**
- Create: `src/main/java/com/travelalbum/dto/request/InviteMemberRequest.java`
- Create: `src/main/java/com/travelalbum/dto/response/EventInviteResponse.java`
- Create: `src/main/java/com/travelalbum/service/EventInviteService.java`
- Create: `src/main/java/com/travelalbum/service/impl/EventInviteServiceImpl.java`
- Test: `src/test/java/com/travelalbum/service/impl/EventInviteServiceImplTest.java` (new)

**Interfaces:**
- Consumes: `EventInviteRepository`, `EventMemberRepository`, `EventRepository`, `UserRepository.findByEmail` (all existing/Task 1).
- Produces: `EventInviteService.invite(Long eventId, String email, EventMemberRole role, Long inviterId):EventInviteResponse`, `.listMine(Long userId):List<EventInviteResponse>`, `.accept(Long inviteId, Long userId):void`, `.decline(Long inviteId, Long userId):void` — consumed by `EventInviteController` in Task 9.

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/travelalbum/service/impl/EventInviteServiceImplTest.java`:
```java
package com.travelalbum.service.impl;

import com.travelalbum.dto.response.EventInviteResponse;
import com.travelalbum.entity.Event;
import com.travelalbum.entity.EventInvite;
import com.travelalbum.entity.User;
import com.travelalbum.enums.EventMemberRole;
import com.travelalbum.enums.InviteStatus;
import com.travelalbum.exception.BusinessException;
import com.travelalbum.exception.NotFoundException;
import com.travelalbum.repository.EventInviteRepository;
import com.travelalbum.repository.EventMemberRepository;
import com.travelalbum.repository.EventRepository;
import com.travelalbum.repository.UserRepository;
import com.travelalbum.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventInviteServiceImplTest {

    @Mock private EventInviteRepository eventInviteRepository;
    @Mock private EventMemberRepository eventMemberRepository;
    @Mock private EventRepository eventRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private EventInviteServiceImpl eventInviteService;

    private Event event() {
        return Event.builder().id(10L).ownerId(1L).name("Da Lat Trip").build();
    }

    @Test
    void invite_throwsAccessDenied_whenRequesterNotOwner() {
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event()));

        assertThatThrownBy(() -> eventInviteService.invite(10L, "member@test.com", EventMemberRole.EDITOR, 2L))
                .isInstanceOf(AccessDeniedException.class);
        verify(eventInviteRepository, never()).save(any());
    }

    @Test
    void invite_throwsNotFound_whenEmailUnknown() {
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event()));
        when(userRepository.findByEmail("nobody@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventInviteService.invite(10L, "nobody@test.com", EventMemberRole.EDITOR, 1L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void invite_throwsBusinessException_whenInvitingSelf() {
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event()));
        when(userRepository.findByEmail("owner@test.com")).thenReturn(Optional.of(User.builder().id(1L).email("owner@test.com").build()));

        assertThatThrownBy(() -> eventInviteService.invite(10L, "owner@test.com", EventMemberRole.EDITOR, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("CANNOT_INVITE_SELF"));
    }

    @Test
    void invite_throwsBusinessException_whenAlreadyMember() {
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event()));
        when(userRepository.findByEmail("member@test.com")).thenReturn(Optional.of(User.builder().id(2L).email("member@test.com").build()));
        when(eventMemberRepository.existsByEventIdAndUserId(10L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> eventInviteService.invite(10L, "member@test.com", EventMemberRole.EDITOR, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("ALREADY_MEMBER"));
    }

    @Test
    void invite_throwsBusinessException_whenAlreadyInvited() {
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event()));
        when(userRepository.findByEmail("member@test.com")).thenReturn(Optional.of(User.builder().id(2L).email("member@test.com").build()));
        when(eventMemberRepository.existsByEventIdAndUserId(10L, 2L)).thenReturn(false);
        when(eventInviteRepository.findByEventIdAndInvitedUserId(10L, 2L))
                .thenReturn(Optional.of(EventInvite.builder().id(99L).build()));

        assertThatThrownBy(() -> eventInviteService.invite(10L, "member@test.com", EventMemberRole.EDITOR, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("ALREADY_INVITED"));
    }

    @Test
    void invite_createsPendingInvite_onHappyPath() {
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event()));
        when(userRepository.findByEmail("member@test.com")).thenReturn(Optional.of(User.builder().id(2L).email("member@test.com").build()));
        when(eventMemberRepository.existsByEventIdAndUserId(10L, 2L)).thenReturn(false);
        when(eventInviteRepository.findByEventIdAndInvitedUserId(10L, 2L)).thenReturn(Optional.empty());
        when(eventInviteRepository.save(any(EventInvite.class))).thenAnswer(inv -> {
            EventInvite i = inv.getArgument(0);
            i.setId(500L);
            return i;
        });

        EventInviteResponse response = eventInviteService.invite(10L, "member@test.com", EventMemberRole.EDITOR, 1L);

        assertThat(response.getId()).isEqualTo(500L);
        assertThat(response.getEventId()).isEqualTo(10L);
        assertThat(response.getEventName()).isEqualTo("Da Lat Trip");
        assertThat(response.getRole()).isEqualTo(EventMemberRole.EDITOR);
        assertThat(response.getStatus()).isEqualTo(InviteStatus.PENDING);
        verify(auditLogService).log(eq(1L), eq("INVITE_MEMBER"), eq("EVENT"), eq(10L), any(), any(), eq("SUCCESS"));
    }

    @Test
    void listMine_returnsOnlyPendingInvites() {
        EventInvite invite = EventInvite.builder().id(1L).event(event()).role(EventMemberRole.VIEWER).status(InviteStatus.PENDING).build();
        when(eventInviteRepository.findByInvitedUserIdAndStatus(2L, InviteStatus.PENDING)).thenReturn(List.of(invite));

        List<EventInviteResponse> result = eventInviteService.listMine(2L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEventName()).isEqualTo("Da Lat Trip");
    }

    @Test
    void accept_createsMemberAndMarksAccepted() {
        EventInvite invite = EventInvite.builder().id(1L).event(event()).invitedUserId(2L)
                .role(EventMemberRole.EDITOR).status(InviteStatus.PENDING).invitedBy(1L).build();
        when(eventInviteRepository.findById(1L)).thenReturn(Optional.of(invite));
        when(eventMemberRepository.existsByEventIdAndUserId(10L, 2L)).thenReturn(false);

        eventInviteService.accept(1L, 2L);

        assertThat(invite.getStatus()).isEqualTo(InviteStatus.ACCEPTED);
        assertThat(invite.getRespondedAt()).isNotNull();
        ArgumentCaptor<com.travelalbum.entity.EventMember> captor = ArgumentCaptor.forClass(com.travelalbum.entity.EventMember.class);
        verify(eventMemberRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(2L);
        assertThat(captor.getValue().getRole()).isEqualTo(EventMemberRole.EDITOR);
    }

    @Test
    void accept_throwsAccessDenied_whenNotOwnInvite() {
        EventInvite invite = EventInvite.builder().id(1L).event(event()).invitedUserId(2L).status(InviteStatus.PENDING).build();
        when(eventInviteRepository.findById(1L)).thenReturn(Optional.of(invite));

        assertThatThrownBy(() -> eventInviteService.accept(1L, 3L)).isInstanceOf(AccessDeniedException.class);
        verify(eventMemberRepository, never()).save(any());
    }

    @Test
    void accept_throwsBusinessException_whenAlreadyResponded() {
        EventInvite invite = EventInvite.builder().id(1L).event(event()).invitedUserId(2L).status(InviteStatus.DECLINED).build();
        when(eventInviteRepository.findById(1L)).thenReturn(Optional.of(invite));

        assertThatThrownBy(() -> eventInviteService.accept(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("INVITE_ALREADY_RESPONDED"));
    }

    @Test
    void decline_marksDeclined_withoutCreatingMember() {
        EventInvite invite = EventInvite.builder().id(1L).event(event()).invitedUserId(2L).status(InviteStatus.PENDING).build();
        when(eventInviteRepository.findById(1L)).thenReturn(Optional.of(invite));

        eventInviteService.decline(1L, 2L);

        assertThat(invite.getStatus()).isEqualTo(InviteStatus.DECLINED);
        verify(eventMemberRepository, never()).save(any());
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -q test -Dtest=EventInviteServiceImplTest`
Expected: compile error (none of the classes under test exist yet).

- [ ] **Step 3: Add the DTOs**

`src/main/java/com/travelalbum/dto/request/InviteMemberRequest.java`:
```java
package com.travelalbum.dto.request;

import com.travelalbum.enums.EventMemberRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InviteMemberRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email is invalid")
    private String email;

    @NotNull(message = "Role is required")
    private EventMemberRole role;
}
```

`src/main/java/com/travelalbum/dto/response/EventInviteResponse.java`:
```java
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
```

- [ ] **Step 4: Add the service interface**

`src/main/java/com/travelalbum/service/EventInviteService.java`:
```java
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
```

- [ ] **Step 5: Implement `EventInviteServiceImpl`**

`src/main/java/com/travelalbum/service/impl/EventInviteServiceImpl.java`:
```java
package com.travelalbum.service.impl;

import com.travelalbum.dto.response.EventInviteResponse;
import com.travelalbum.entity.Event;
import com.travelalbum.entity.EventInvite;
import com.travelalbum.entity.EventMember;
import com.travelalbum.entity.User;
import com.travelalbum.enums.EventMemberRole;
import com.travelalbum.enums.InviteStatus;
import com.travelalbum.exception.BusinessException;
import com.travelalbum.exception.NotFoundException;
import com.travelalbum.repository.EventInviteRepository;
import com.travelalbum.repository.EventMemberRepository;
import com.travelalbum.repository.EventRepository;
import com.travelalbum.repository.UserRepository;
import com.travelalbum.service.AuditLogService;
import com.travelalbum.service.EventInviteService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EventInviteServiceImpl implements EventInviteService {

    private final EventInviteRepository eventInviteRepository;
    private final EventMemberRepository eventMemberRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public EventInviteResponse invite(Long eventId, String email, EventMemberRole role, Long inviterId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found"));
        if (!event.getOwnerId().equals(inviterId)) {
            throw new AccessDeniedException("Not the owner of this event");
        }
        User invitedUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found"));
        if (invitedUser.getId().equals(inviterId)) {
            throw new BusinessException("Cannot invite yourself", "CANNOT_INVITE_SELF");
        }
        if (eventMemberRepository.existsByEventIdAndUserId(eventId, invitedUser.getId())) {
            throw new BusinessException("User is already a member of this event", "ALREADY_MEMBER");
        }
        if (eventInviteRepository.findByEventIdAndInvitedUserId(eventId, invitedUser.getId()).isPresent()) {
            throw new BusinessException("User already has an invite for this event", "ALREADY_INVITED");
        }

        EventInvite invite = EventInvite.builder()
                .event(event)
                .invitedUserId(invitedUser.getId())
                .role(role)
                .status(InviteStatus.PENDING)
                .invitedBy(inviterId)
                .build();
        EventInvite saved = eventInviteRepository.save(invite);
        auditLogService.log(inviterId, "INVITE_MEMBER", "EVENT", eventId, null, null, "SUCCESS");

        return toResponse(saved, event.getName());
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventInviteResponse> listMine(Long userId) {
        // @Transactional bắt buộc — invite.getEvent().getName() chạm vào quan hệ LAZY,
        // giống lý do ShareServiceImpl.getEventByToken cần transaction mở sẵn.
        return eventInviteRepository.findByInvitedUserIdAndStatus(userId, InviteStatus.PENDING).stream()
                .map(i -> toResponse(i, i.getEvent().getName()))
                .toList();
    }

    @Override
    @Transactional
    public void accept(Long inviteId, Long userId) {
        EventInvite invite = getOwnPendingInvite(inviteId, userId);
        invite.setStatus(InviteStatus.ACCEPTED);
        invite.setRespondedAt(LocalDateTime.now());
        eventInviteRepository.save(invite);

        Long eventId = invite.getEvent().getId();
        if (!eventMemberRepository.existsByEventIdAndUserId(eventId, userId)) {
            EventMember member = EventMember.builder()
                    .event(invite.getEvent())
                    .userId(userId)
                    .role(invite.getRole())
                    .invitedBy(invite.getInvitedBy())
                    .build();
            eventMemberRepository.save(member);
        }
        auditLogService.log(userId, "ACCEPT_INVITE", "EVENT", eventId, null, null, "SUCCESS");
    }

    @Override
    @Transactional
    public void decline(Long inviteId, Long userId) {
        EventInvite invite = getOwnPendingInvite(inviteId, userId);
        invite.setStatus(InviteStatus.DECLINED);
        invite.setRespondedAt(LocalDateTime.now());
        eventInviteRepository.save(invite);
    }

    private EventInvite getOwnPendingInvite(Long inviteId, Long userId) {
        EventInvite invite = eventInviteRepository.findById(inviteId)
                .orElseThrow(() -> new NotFoundException("Invite not found"));
        if (!invite.getInvitedUserId().equals(userId)) {
            throw new AccessDeniedException("Not your invite");
        }
        if (invite.getStatus() != InviteStatus.PENDING) {
            throw new BusinessException("Invite already responded", "INVITE_ALREADY_RESPONDED");
        }
        return invite;
    }

    private EventInviteResponse toResponse(EventInvite invite, String eventName) {
        return EventInviteResponse.builder()
                .id(invite.getId())
                .eventId(invite.getEvent().getId())
                .eventName(eventName)
                .role(invite.getRole())
                .status(invite.getStatus())
                .createdAt(invite.getCreatedAt())
                .build();
    }
}
```

- [ ] **Step 6: Run to verify it passes**

Run: `mvn -q test -Dtest=EventInviteServiceImplTest`
Expected: PASS, all tests green.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/travelalbum/dto/request/InviteMemberRequest.java \
        src/main/java/com/travelalbum/dto/response/EventInviteResponse.java \
        src/main/java/com/travelalbum/service/EventInviteService.java \
        src/main/java/com/travelalbum/service/impl/EventInviteServiceImpl.java \
        src/test/java/com/travelalbum/service/impl/EventInviteServiceImplTest.java
git commit -m "feat: add EventInviteService (invite by email, accept/decline)"
```

---

### Task 9: `EventInviteController`

**Files:**
- Create: `src/main/java/com/travelalbum/controller/EventInviteController.java`

**Interfaces:**
- Consumes: `EventInviteService` (Task 8), `EventSecurity.isOwner` (existing).

No new tests (no controller test harness); verified by `mvn test` + Task 10 smoke check.

- [ ] **Step 1: Add the controller**

`src/main/java/com/travelalbum/controller/EventInviteController.java`:
```java
package com.travelalbum.controller;

import com.travelalbum.common.ApiResponse;
import com.travelalbum.dto.request.InviteMemberRequest;
import com.travelalbum.dto.response.EventInviteResponse;
import com.travelalbum.security.userdetails.UserPrincipal;
import com.travelalbum.service.EventInviteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class EventInviteController {

    private final EventInviteService eventInviteService;

    @PostMapping("/api/events/{id}/members/invite")
    @PreAuthorize("hasRole('ADMIN') or @eventSecurity.isOwner(#id, authentication)")
    public ApiResponse<EventInviteResponse> invite(@PathVariable Long id,
                                                     @Valid @RequestBody InviteMemberRequest req,
                                                     @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success("Invite sent",
                eventInviteService.invite(id, req.getEmail(), req.getRole(), principal.getId()));
    }

    @GetMapping("/api/invites/me")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<List<EventInviteResponse>> listMine(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success("OK", eventInviteService.listMine(principal.getId()));
    }

    @PostMapping("/api/invites/{id}/accept")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<Void> accept(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        eventInviteService.accept(id, principal.getId());
        return ApiResponse.success("Invite accepted", null);
    }

    @PostMapping("/api/invites/{id}/decline")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<Void> decline(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        eventInviteService.decline(id, principal.getId());
        return ApiResponse.success("Invite declined", null);
    }
}
```

- [ ] **Step 2: Compile + run full suite**

Run: `mvn -q test`
Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/travelalbum/controller/EventInviteController.java
git commit -m "feat: add EventInviteController (invite/list/accept/decline endpoints)"
```

---

### Task 10: Full verification

**Files:** none (verification only)

- [ ] **Step 1: Run the full test suite**

Run: `mvn -q test`
Expected: `BUILD SUCCESS`, 0 failures.

- [ ] **Step 2: Full compile (including any test-only code paths)**

Run: `mvn -q clean verify -DskipITs`
Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Review the final diff against the spec**

Run: `git log --oneline main..HEAD` and `git diff main --stat`
Confirm every row of the spec's API table (`docs/superpowers/specs/2026-08-16-event-membership-design.md`) has a corresponding change, and that no unrelated files were touched.

- [ ] **Step 4: Use superpowers:requesting-code-review before merging**

Follow that skill to get the branch reviewed against `docs/superpowers/specs/2026-08-16-event-membership-design.md` and this plan before opening a PR.
