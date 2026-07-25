-- Xác thực & hiển thị rõ email Gmail sau khi Login (SEC-12)
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT false
    COMMENT 'Lấy từ attribute email_verified do Google trả về khi login OAuth2, chỉ mang tính hiển thị/tham khảo';
