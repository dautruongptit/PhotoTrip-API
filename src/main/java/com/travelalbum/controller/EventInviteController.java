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
                eventInviteService.invite(id, req.getEmail(), req.getRole(), principal.getId(), principal.isAdmin()));
    }

    @GetMapping("/api/invites/me")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<List<EventInviteResponse>> listMine(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success("OK", eventInviteService.listMine(principal.getId()));
    }

    @PostMapping("/api/invites/{id}/accept")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<Long> accept(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success("Invite accepted", eventInviteService.accept(id, principal.getId()));
    }

    @PostMapping("/api/invites/{id}/decline")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<Void> decline(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        eventInviteService.decline(id, principal.getId());
        return ApiResponse.success("Invite declined", null);
    }
}
