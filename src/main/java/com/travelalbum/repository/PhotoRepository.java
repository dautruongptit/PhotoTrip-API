package com.travelalbum.repository;

import com.travelalbum.entity.Photo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PhotoRepository extends JpaRepository<Photo, Long> {

    boolean existsByEventIdAndOriginalName(Long eventId, String originalName);

    Optional<Photo> findByChecksumAndEventId(String checksum, Long eventId);

    Page<Photo> findByEventId(Long eventId, Pageable pageable);

    List<Photo> findByIdIn(List<Long> ids);

    @Query("SELECT COUNT(p) FROM Photo p WHERE p.event.id = :eventId")
    long countByEvent(@Param("eventId") Long eventId);

    @Query("SELECT p FROM Photo p WHERE lower(p.originalName) LIKE lower(concat('%', :kw, '%'))")
    Page<Photo> search(@Param("kw") String keyword, Pageable pageable);

    /** Đếm ảnh upload trong khoảng thời gian — dùng cho "Upload hôm nay" (SEC-01/SEC-15). */
    long countByUploadedTimeBetween(LocalDateTime from, LocalDateTime to);

    /**
     * Nhóm số lượng ảnh upload theo tháng (dùng date_trunc của PostgreSQL) — phục vụ
     * biểu đồ "Upload theo tháng" ở Dashboard Admin (SEC-01/SEC-15).
     * Trả về mỗi hàng: [0]=tháng dạng yyyy-MM-01 (java.sql.Date), [1]=số lượng (Long).
     */
    // Sau (MySQL):
    @Query(value = "SELECT DATE_FORMAT(uploaded_time, '%Y-%m') AS month, COUNT(*) AS cnt "
            + "FROM photos WHERE uploaded_time >= :from GROUP BY month ORDER BY month",
            nativeQuery = true)
    List<Object[]> countUploadsGroupedByMonth(@Param("from") LocalDateTime from);

}
