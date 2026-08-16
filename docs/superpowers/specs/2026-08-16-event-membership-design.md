# Event Membership (VIEWER/EDITOR) — Design Spec

Date: 2026-08-16
Status: Approved for implementation

## Problem

Hiện tại quyền truy cập một `Event` chỉ có hai mức: **owner** (toàn quyền) và
**khách ẩn danh qua `ShareLink`** (chỉ xem, không gắn với user nào — role
`VIEWER` chỉ tồn tại ở tầng Security cho request không có JWT).

Không có khái niệm "member": user đã đăng nhập khác owner **không thể** upload
ảnh vào event dù được share, và cũng không xem được event/danh sách ảnh qua
API thường (`GET /api/events/{id}`, `GET /api/events/{id}/photos`) — các API
này đang `@PreAuthorize` cứng `eventSecurity.isOwner()`.

`PhotoServiceImpl.uploadMultiple` (dòng 55) chặn cứng: chỉ owner mới upload
được. `PhotoController.upload` thậm chí **không có** `@eventSecurity` check ở
tầng controller (khác các endpoint khác), chỉ dựa vào check trong service.

## Goal

Thêm khái niệm **member** của event với 2 role:
- `VIEWER`: xem event + danh sách ảnh.
- `EDITOR`: xem + upload ảnh + xóa ảnh do chính mình upload.

Owner giữ nguyên toàn quyền (kể cả trên ảnh do member khác upload). Hai cách
để trở thành member:
1. **Qua share link**: owner tạo `ShareLink` gắn kèm 1 role (`VIEWER` hoặc
   `EDITOR`, mặc định `VIEWER`). Bất kỳ user đã đăng nhập nào cầm token gọi
   `POST /api/share/{token}/join` sẽ được thêm làm member với role của link.
2. **Owner mời trực tiếp bằng email**: không có hạ tầng gửi email thật trong
   dự án (không SMTP/JavaMailSender) — invite chỉ lưu DB ở trạng thái
   `PENDING`, người được mời (phải là user đã có tài khoản, tra theo email)
   thấy invite của mình qua API riêng và tự accept/decline.

## Non-goals (YAGNI — không làm trong phạm vi này)

- Gửi email thật (SMTP integration).
- API liệt kê/xóa member khỏi event (owner quản lý danh sách member).
- Đổi role của member đã tồn tại.
- Member tự rời khỏi event.

Đây là các tính năng hợp lý để làm tiếp sau, không cần cho yêu cầu hiện tại
(cho member upload + check quyền theo member).

## Data model

Enum mới `com.travelalbum.enums.EventMemberRole { VIEWER, EDITOR }`.

### Migration `V10__add_event_membership.sql`

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

`uq_event_invite_pending` đơn giản hoá: một user chỉ có tối đa 1 invite (ở bất
kỳ status nào) mỗi event. Nếu decline rồi muốn mời lại, cần xoá/update bản ghi
cũ thay vì insert mới — chấp nhận giới hạn này (YAGNI, không cần lịch sử
invite).

### Entities

- `EventMember` (id, event manyToOne LAZY, userId, role, invitedBy, joinedAt).
- `EventInvite` (id, event manyToOne LAZY, invitedUserId, role, status enum
  `InviteStatus{PENDING,ACCEPTED,DECLINED}`, invitedBy, createdAt, respondedAt).
- `ShareLink` thêm field `role` (`EventMemberRole`, `@Enumerated(STRING)`,
  default `VIEWER` qua `@Builder.Default`).

### Repositories

`EventMemberRepository`:
- `boolean existsByEventIdAndUserId(Long eventId, Long userId)`
- `boolean existsByEventIdAndUserIdAndRole(Long eventId, Long userId, EventMemberRole role)`
- `Optional<EventMember> findByEventIdAndUserId(...)` (dùng khi join qua share link, tránh insert trùng)

`EventInviteRepository`:
- `List<EventInvite> findByInvitedUserIdAndStatus(Long userId, InviteStatus status)`
- `Optional<EventInvite> findById(...)` (kế thừa JpaRepository)

## API surface

| Method | Path | Auth | Mô tả |
|---|---|---|---|
| POST | `/api/events/{id}/share` | owner/admin | thêm `role` (VIEWER\|EDITOR, default VIEWER) vào `CreateShareLinkRequest`/query param |
| POST | `/api/share/{token}/join` | user đã login | tạo `event_members` nếu chưa là member; nếu đã là member, không đổi role (idempotent) |
| POST | `/api/events/{id}/members/invite` | owner | body `{email, role}` → tìm `User` theo email (404 nếu không có), tạo `EventInvite` PENDING (409 nếu đã có invite/đã là member) |
| GET | `/api/invites/me` | user đã login | list `EventInvite` PENDING của user hiện tại |
| POST | `/api/invites/{id}/accept` | user đã login (phải là invited_user_id) | set ACCEPTED, tạo `EventMember` |
| POST | `/api/invites/{id}/decline` | user đã login (phải là invited_user_id) | set DECLINED |
| GET | `/api/events/{id}` | owner/admin/**member** | đổi `@PreAuthorize` sang `eventSecurity.canView` |
| GET | `/api/events/{id}/photos` | owner/admin/**member** | đổi sang `eventSecurity.canView` |
| POST | `/api/events/{eventId}/photos` (upload) | owner/admin/**EDITOR member** | thêm `@PreAuthorize("hasRole('ADMIN') or @eventSecurity.canUpload(#eventId, authentication)")`; sửa check cứng trong `PhotoServiceImpl.uploadMultiple` |
| DELETE | `/api/photos/{id}` | owner/admin/**uploader chính chủ** | sửa `PhotoServiceImpl.delete`: cho phép nếu `photo.uploadedBy == requesterId`, ngoài owner/admin |

`EventSecurity` thêm:
```java
public boolean canView(Long eventId, Authentication authentication) {
    UserPrincipal p = (UserPrincipal) authentication.getPrincipal();
    return isOwner(eventId, authentication)
        || eventMemberRepository.existsByEventIdAndUserId(eventId, p.getId());
}

public boolean canUpload(Long eventId, Authentication authentication) {
    UserPrincipal p = (UserPrincipal) authentication.getPrincipal();
    return isOwner(eventId, authentication)
        || eventMemberRepository.existsByEventIdAndUserIdAndRole(eventId, p.getId(), EventMemberRole.EDITOR);
}
```

## Service logic changes

- `PhotoServiceImpl.uploadMultiple`: thay
  `if (!event.getOwnerId().equals(userId)) throw AccessDeniedException(...)`
  bằng owner-or-editor check (cần `EventMemberRepository`). Phần còn lại
  (quota theo `userId` đang upload, storage folder theo uploader) **giữ
  nguyên** — đã đúng thiết kế sẵn (member dùng quota của chính họ).
- `PhotoServiceImpl.delete`: thay điều kiện chặn
  `if (!isAdmin && !ownerId.equals(requesterId)) throw ...` bằng
  `if (!isAdmin && !ownerId.equals(requesterId) && !requesterId.equals(photo.getUploadedBy())) throw ...`.
  Không cần re-check role EDITOR hiện tại — chỉ EDITOR/owner mới có thể là
  `uploadedBy` ngay từ đầu.
- `ShareServiceImpl.create`: nhận thêm `role` param, set vào `ShareLink`.
- `ShareServiceImpl` thêm `joinByToken(String token, Long userId)`: tìm link
  active & chưa hết hạn (tái dùng logic hiện có), nếu chưa có
  `EventMember(eventId, userId)` thì tạo với role của link; nếu đã có thì
  no-op (không hạ role nếu họ từng được mời làm EDITOR qua đường khác).
- `EventInviteServiceImpl` (mới) implement invite/accept/decline/listMine như
  bảng API trên.

## Error handling

- Invite tới email chưa có tài khoản → `NotFoundException("User not found")`.
- Invite trùng (đã PENDING hoặc đã là member) → `BusinessException("...", "ALREADY_INVITED"/"ALREADY_MEMBER")`.
- Accept/decline invite không phải của mình → `AccessDeniedException`.
- Join share link hết hạn/không active → tái dùng `NotFoundException("Share link not found or expired")` đã có.
- Upload/view không đủ quyền → giữ nguyên `AccessDeniedException` qua `@PreAuthorize` (Spring Security trả 403).

## Testing

- Unit test `EventSecurityTest`/`PhotoSecurityTest` style hiện có (nếu có) cho `canView`/`canUpload` — owner, editor-member, viewer-member, không phải member.
- `PhotoServiceImplTest`: upload thành công cho EDITOR member, 403 cho VIEWER member, 403 cho người ngoài; delete: EDITOR xoá được ảnh của chính mình, không xoá được ảnh người khác upload.
- `ShareServiceImplTest`: join qua link VIEWER vs EDITOR ra đúng role; join 2 lần không tạo trùng bản ghi (unique constraint).
- Integration test (nếu dự án có `@SpringBootTest` cho controller) cho luồng: tạo share link role=EDITOR → user khác join → user đó upload thành công.

## Files touched

- **New**: `enums/EventMemberRole.java`, `enums/InviteStatus.java`,
  `entity/EventMember.java`, `entity/EventInvite.java`,
  `repository/EventMemberRepository.java`, `repository/EventInviteRepository.java`,
  `service/EventInviteService.java` + impl, `controller/EventInviteController.java`,
  `dto/request/InviteMemberRequest.java`,
  `dto/response/EventInviteResponse.java`,
  `db/migration/V10__add_event_membership.sql`.
- **Modified**: `entity/ShareLink.java`, `security/EventSecurity.java`,
  `service/ShareService.java` + impl, `controller/ShareController.java`,
  `controller/EventController.java`, `controller/PhotoController.java`,
  `service/impl/PhotoServiceImpl.java`.
