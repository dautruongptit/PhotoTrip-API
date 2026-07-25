-- MySQL không có pg_trgm (substring search). Dùng FULLTEXT INDEX thay thế.
-- Lưu ý: FULLTEXT match theo TỪ (word-based), không match substring tùy ý
-- như LIKE '%kw%' của pg_trgm. Muốn giữ đúng hành vi substring, vẫn có thể
-- dùng LIKE '%kw%' bình thường (sẽ chậm hơn với dữ liệu lớn vì không dùng
-- được index B-Tree cho substring giữa chuỗi).
ALTER TABLE events ADD FULLTEXT INDEX ft_events_name (name);
ALTER TABLE photos ADD FULLTEXT INDEX ft_photos_original_name (original_name);
