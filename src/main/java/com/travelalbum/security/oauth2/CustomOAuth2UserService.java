package com.travelalbum.security.oauth2;

import com.travelalbum.entity.User;
import com.travelalbum.enums.Role;
import com.travelalbum.enums.UserStatus;
import com.travelalbum.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Tạo/lấy User khi login Google — cập nhật cờ emailVerified mỗi lần login (SEC-12).
 * KHÔNG chặn đăng nhập dù emailVerified = false, chỉ lưu lại để hiển thị.
 */
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) {
        OAuth2User oAuth2User = super.loadUser(request);
        Map<String, Object> attrs = oAuth2User.getAttributes();

        String googleId = (String) attrs.get("sub");
        String email = (String) attrs.get("email");
        String name = (String) attrs.get("name");
        String avatar = (String) attrs.get("picture");
        boolean emailVerified = Boolean.TRUE.equals(attrs.get("email_verified"));

        User user = userRepository.findByGoogleId(googleId)
            .map(u -> {
                u.setFullName(name);
                u.setAvatarUrl(avatar);
                u.setEmailVerified(emailVerified);
                return u;
            })
            .orElseGet(() -> User.builder()
                .googleId(googleId)
                .email(email)
                .fullName(name)
                .avatarUrl(avatar)
                .emailVerified(emailVerified)
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .storageUsed(0L)
                .storageQuota(5L * 1024 * 1024 * 1024)
                .build());
        userRepository.save(user);

        return new DefaultOAuth2User(
            List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())),
            attrs, "sub");
    }
}
