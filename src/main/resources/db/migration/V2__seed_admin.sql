INSERT INTO users (email, full_name, google_id, role, status)
VALUES ('admin@travelalbum.com', 'System Admin', 'MANUAL_SEED_ADMIN', 'ADMIN', 'ACTIVE')
ON DUPLICATE KEY UPDATE email = email;

-- Lưu ý: tài khoản này chỉ dùng tạm để bootstrap quyền ADMIN đầu tiên;
-- nên gán role ADMIN cho user thật qua Google Login rồi xoá bản ghi seed này.
