package com.travelalbum.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** Chỉ dùng ở endpoint dev-login (SEC-25) — KHÔNG liên quan luồng Google OAuth2 thật. */
@Getter
@Setter
public class DevLoginRequest {

    @NotBlank(message = "Email is required")
    @Email
    private String email;

    /** Tuỳ chọn — mặc định "Dev Tester" nếu bỏ trống, chỉ áp dụng khi tạo user mới. */
    private String fullName;
}