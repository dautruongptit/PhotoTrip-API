package com.travelalbum.controller;

import com.travelalbum.audit.Auditable;
import com.travelalbum.common.ApiResponse;
import com.travelalbum.dto.request.UpdateProfileRequest;
import com.travelalbum.dto.response.UserResponse;
import com.travelalbum.security.userdetails.UserPrincipal;
import com.travelalbum.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/api/users/profile")
    public ApiResponse<UserResponse> profile(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success("OK", userService.getProfile(principal.getId()));
    }

    @Auditable(action = "UPDATE_PROFILE", targetType = "USER")
    @PutMapping("/api/users/profile")
    public ApiResponse<UserResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest req,
                                                     @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success("Profile updated", userService.updateProfile(principal.getId(), req));
    }

    @GetMapping("/api/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Page<UserResponse>> list(Pageable pageable) {
        return ApiResponse.success("OK", userService.listAll(pageable));
    }

    @DeleteMapping("/api/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        userService.deleteUser(id, principal.getId());
        return ApiResponse.success("User disabled", null);
    }
}
