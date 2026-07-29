-- SEC-27: Bổ sung field theo form "Tạo sự kiện" thật của Frontend
-- (Ngày bắt đầu, Ngày kết thúc, Địa điểm, Ảnh bìa)

-- Đổi tên cột cho đúng ngữ nghĩa: event_date (SEC-26) -> start_date
ALTER TABLE events RENAME COLUMN event_date TO start_date;

ALTER TABLE events ADD COLUMN end_date DATE NULL
  COMMENT 'Ngày kết thúc sự kiện — tuỳ chọn, chỉ hiển thị, không dùng trong tên thư mục';

ALTER TABLE events ADD COLUMN location VARCHAR(255) NULL
  COMMENT 'Địa điểm sự kiện, vd "Đà Lạt, Lâm Đồng"';

ALTER TABLE events ADD COLUMN cover_image_path VARCHAR(500) NULL
  COMMENT 'Path tương đối của ảnh bìa trong storage, giống photos.path — không lưu path tuyệt đối';