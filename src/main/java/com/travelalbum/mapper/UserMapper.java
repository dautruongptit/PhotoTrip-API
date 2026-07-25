package com.travelalbum.mapper;

import com.travelalbum.entity.User;
import org.springframework.stereotype.Component;

/**
 * Mapper rút gọn phục vụ PhotoMapper.uploadedBy — trả về tên hiển thị của User.
 * (UserResponse.from() ở SEC-12 dùng trực tiếp, không qua MapStruct.)
 */
@Component
public class UserMapper {

    public String toDisplayName(User user) {
        if (user == null) {
            return null;
        }
        return user.getFullName() != null ? user.getFullName() : user.getEmail();
    }
}
