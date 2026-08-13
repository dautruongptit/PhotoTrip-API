package com.travelalbum.service.impl;

import com.travelalbum.dto.request.UpdateProfileRequest;
import com.travelalbum.dto.response.UserResponse;
import com.travelalbum.entity.User;
import com.travelalbum.enums.Role;
import com.travelalbum.enums.UserStatus;
import com.travelalbum.exception.NotFoundException;
import com.travelalbum.repository.UserRepository;
import com.travelalbum.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private UserServiceImpl userService;

    private User activeUser() {
        return User.builder()
                .id(1L)
                .email("dautruong@gmail.com")
                .fullName("Dau Truong")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .build();
    }

    @Test
    void getProfile_returnsMappedResponse() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser()));

        UserResponse response = userService.getProfile(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getEmail()).isEqualTo("dautruong@gmail.com");
        assertThat(response.getFullName()).isEqualTo("Dau Truong");
        assertThat(response.getRole()).isEqualTo("USER");
        assertThat(response.isEmailVerified()).isTrue();
    }

    @Test
    void getProfile_throwsNotFound_whenUserMissing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile(99L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateProfile_updatesFullNameOnly() {
        User user = activeUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setFullName("Dau Van Truong");

        UserResponse response = userService.updateProfile(1L, req);

        assertThat(response.getFullName()).isEqualTo("Dau Van Truong");
        assertThat(user.getEmail()).isEqualTo("dautruong@gmail.com"); // không đổi field khác
    }

    @Test
    void updateProfile_throwsNotFound_whenUserMissing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateProfile(99L, new UpdateProfileRequest()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteUser_disablesInsteadOfHardDelete() {
        User user = activeUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.deleteUser(1L, 999L);

        assertThat(user.getStatus()).isEqualTo(UserStatus.DISABLED);
        verify(userRepository).save(user);
        verify(userRepository, org.mockito.Mockito.never()).delete(any());
        verify(auditLogService).log(eq(999L), eq("ADMIN_DELETE_USER"), eq("USER"), eq(1L), any(), any(), eq("SUCCESS"));
    }

    @Test
    void deleteUser_throwsNotFound_whenUserMissing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteUser(99L, 1L)).isInstanceOf(NotFoundException.class);
    }
}
