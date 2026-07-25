package com.travelalbum.service;

import com.travelalbum.dto.request.UpdateProfileRequest;
import com.travelalbum.dto.response.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {
    UserResponse getProfile(Long userId);
    UserResponse updateProfile(Long userId, UpdateProfileRequest req);
    Page<UserResponse> listAll(Pageable pageable);
    void deleteUser(Long id, Long adminId);
}
