package com.travelalbum.controller;

import com.travelalbum.common.ApiResponse;
import com.travelalbum.dto.response.EventResponse;
import com.travelalbum.dto.response.PhotoResponse;
import com.travelalbum.security.userdetails.UserPrincipal;
import com.travelalbum.service.EventService;
import com.travelalbum.service.PhotoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SearchController {

    private final EventService eventService;
    private final PhotoService photoService;

    @GetMapping("/api/events/search")
    public ApiResponse<Page<EventResponse>> searchEvents(@RequestParam String keyword, Pageable pageable,
                                                          @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success("OK", eventService.search(keyword, pageable, principal.getId(), principal.isAdmin()));
    }

    @GetMapping("/api/photos/search")
    public ApiResponse<Page<PhotoResponse>> searchPhotos(@RequestParam String keyword, Pageable pageable,
                                                          @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success("OK", photoService.search(keyword, pageable, principal.getId(), principal.isAdmin()));
    }
}