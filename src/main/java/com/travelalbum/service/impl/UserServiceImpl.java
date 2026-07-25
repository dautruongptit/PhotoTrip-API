package com.travelalbum.service.impl;

import com.travelalbum.dto.request.UpdateProfileRequest;
import com.travelalbum.dto.response.UserResponse;
import com.travelalbum.entity.User;
import com.travelalbum.enums.UserStatus;
import com.travelalbum.exception.NotFoundException;
import com.travelalbum.repository.UserRepository;
import com.travelalbum.service.AuditLogService;
import com.travelalbum.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Override
    public UserResponse getProfile(Long userId) {
        return UserResponse.from(userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found")));
    }

    @Override
    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest req) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found"));
        user.setFullName(req.getFullName());
        return UserResponse.from(userRepository.save(user));
    }

    @Override
    public Page<UserResponse> listAll(Pageable pageable) {
        return userRepository.findAll(pageable).map(UserResponse::from);
    }

    @Override
    @Transactional
    public void deleteUser(Long id, Long adminId) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("User not found"));
        // Vô hiệu hoá thay vì xoá cứng để giữ toàn vẹn dữ liệu Event/Photo liên quan
        user.setStatus(UserStatus.DISABLED);
        userRepository.save(user);
        auditLogService.log(adminId, "ADMIN_DELETE_USER", "USER", id, null, null, "SUCCESS");
    }
}
