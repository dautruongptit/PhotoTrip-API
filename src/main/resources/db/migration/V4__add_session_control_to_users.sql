-- Single Session Login — chỉ 1 token/session hoạt động tại 1 thời điểm/user (SEC-10)
ALTER TABLE users ADD COLUMN current_session_id VARCHAR(64)
    COMMENT 'Session đang active duy nhất; JWT nào có sid khác giá trị này sẽ bị coi là hết hiệu lực';
ALTER TABLE users ADD COLUMN last_login_at TIMESTAMP NULL
    COMMENT 'Thời điểm đăng nhập gần nhất';
ALTER TABLE users ADD COLUMN last_login_ip VARCHAR(45)
    COMMENT 'IP của lần đăng nhập gần nhất';
