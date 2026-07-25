-- Storage theo User: quota lưu trữ (SEC-11)
ALTER TABLE users ADD COLUMN storage_used BIGINT NOT NULL DEFAULT 0
    COMMENT 'Tổng dung lượng (bytes) user đã dùng, cập nhật mỗi lần upload/xoá ảnh';
ALTER TABLE users ADD COLUMN storage_quota BIGINT NOT NULL DEFAULT 5368709120
    COMMENT 'Giới hạn dung lượng cho phép (bytes), ADMIN có thể chỉnh riêng từng user'; -- mặc định 5GB/user
