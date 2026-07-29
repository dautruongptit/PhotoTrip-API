-- SEC-26: Quy tắc đặt tên thư mục lưu trữ mới.
-- Chỉ 2 cột thật sự cần thêm mới — mọi thứ khác tái sử dụng cột đã có sẵn.

ALTER TABLE users ADD COLUMN storage_folder VARCHAR(255)
    COMMENT 'Tên thư mục cha vật lý dạng {username}_{parentFolderCode} (SEC-26), cố định từ lần đầu tạo, không đổi theo email';

ALTER TABLE events ADD COLUMN event_date DATE
    COMMENT 'Ngày bắt đầu sự kiện do người dùng nhập, dùng làm phần yyyyMMdd trong tên thư mục (SEC-26)';