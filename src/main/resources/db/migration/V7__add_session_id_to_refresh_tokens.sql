-- Bổ sung liên kết refresh_tokens <-> session hiện tại của user (phát hiện thiếu ở SEC-13)
ALTER TABLE refresh_tokens ADD COLUMN session_id VARCHAR(64)
    COMMENT 'Liên kết với users.current_session_id (SEC-10) để refresh token tự động vô hiệu khi có phiên đăng nhập mới';
CREATE INDEX idx_refresh_session ON refresh_tokens(session_id);
