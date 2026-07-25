package com.travelalbum.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * Trừu tượng hoá thao tác lưu trữ ảnh — cho phép thay Local Disk bằng
 * S3/MinIO sau này (SEC-01/SEC-11) mà không đổi tầng Service.
 * Mọi thao tác đều gắn với userId để đảm bảo cô lập dữ liệu theo user (SEC-11).
 */
public interface StorageService {

    void createUserRootFolder(Long userId);

    void createEventFolder(Long userId, String eventFolder);

    StoredFile store(Long userId, String eventFolder, MultipartFile file);

    Resource load(Long userId, String relativePath);

    void delete(Long userId, String relativePath);

    void deleteEventFolder(Long userId, String eventFolder);

    void deleteUserRootFolder(Long userId);

    long calculateUsedSpace(Long userId);

    /** Tổng dung lượng đĩa vật lý nơi lưu ảnh (bytes) — dùng cho Dashboard Admin (SEC-01/SEC-15). */
    long getDiskTotalSpace();

    /** Dung lượng đĩa còn trống thực tế (bytes) — dùng cho cảnh báo Storage Monitoring. */
    long getDiskUsableSpace();
}
