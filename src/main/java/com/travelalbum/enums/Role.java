package com.travelalbum.enums;

/**
 * Vai trò user trong hệ thống.
 * VIEWER không được lưu trong bảng users — đại diện cho người dùng ẩn danh
 * truy cập qua Share Link, chỉ tồn tại ở tầng Security (authorities), không có bản ghi User.
 */
public enum Role {
    VIEWER,
    USER,
    ADMIN
}
