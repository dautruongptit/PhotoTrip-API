package com.travelalbum.controller;

import com.travelalbum.common.ApiResponse;
import com.travelalbum.dto.response.EventResponse;
import com.travelalbum.dto.response.PhotoResponse;
import com.travelalbum.service.EventService;
import com.travelalbum.service.PhotoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gom toàn bộ endpoint tìm kiếm vào 1 Controller riêng theo đúng bảng API ở SEC-01,
 * thay vì rải rác trong EventController/PhotoController — bổ sung ở SEC-15.
 * Dùng chung EventRepository.search()/PhotoRepository.search() đã có từ SEC-03.
 */
@RestController
@RequiredArgsConstructor
public class SearchController {

    private final EventService eventService;
    private final PhotoService photoService;

    @GetMapping("/api/events/search")
    public ApiResponse<Page<EventResponse>> searchEvents(@RequestParam String keyword, Pageable pageable) {
        return ApiResponse.success("OK", eventService.search(keyword, pageable));
    }

    @GetMapping("/api/photos/search")
    public ApiResponse<Page<PhotoResponse>> searchPhotos(@RequestParam String keyword, Pageable pageable) {
        return ApiResponse.success("OK", photoService.search(keyword, pageable));
    }
}
