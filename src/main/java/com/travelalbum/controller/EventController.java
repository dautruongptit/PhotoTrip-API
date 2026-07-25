package com.travelalbum.controller;

import com.travelalbum.audit.Auditable;
import com.travelalbum.common.ApiResponse;
import com.travelalbum.dto.request.CreateEventRequest;
import com.travelalbum.dto.request.UpdateEventRequest;
import com.travelalbum.dto.response.EventResponse;
import com.travelalbum.security.userdetails.UserPrincipal;
import com.travelalbum.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @GetMapping
    public ApiResponse<Page<EventResponse>> list(Pageable pageable) {
        return ApiResponse.success("OK", eventService.list(pageable));
    }

    // GET /api/events/search đã chuyển sang SearchController riêng — xem SEC-15

    @GetMapping("/{id}")
    public ApiResponse<EventResponse> getById(@PathVariable Long id) {
        return ApiResponse.success("OK", eventService.getById(id));
    }

    @Auditable(action = "CREATE_EVENT", targetType = "EVENT")
    @PostMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<EventResponse> create(@Valid @RequestBody CreateEventRequest req,
                                              @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success("Event created", eventService.create(req, principal.getId()));
    }

    @Auditable(action = "UPDATE_EVENT", targetType = "EVENT")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @eventSecurity.isOwner(#id, authentication)")
    public ApiResponse<EventResponse> update(@PathVariable Long id,
                                              @Valid @RequestBody UpdateEventRequest req,
                                              @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success("Event updated", eventService.update(id, req, principal.getId()));
    }

    @Auditable(action = "DELETE_EVENT", targetType = "EVENT")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @eventSecurity.isOwner(#id, authentication)")
    public ApiResponse<Void> delete(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        eventService.delete(id, principal.getId(), principal.isAdmin());
        return ApiResponse.success("Event deleted", null);
    }
}
