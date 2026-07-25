package com.travelalbum.repository;

import com.travelalbum.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByGoogleId(String googleId);

    Optional<User> findByEmail(String email);

    /** Top N user theo dung lượng đã dùng — phục vụ Dashboard "Top User Upload" (SEC-01/SEC-15). */
    List<User> findTop5ByOrderByStorageUsedDesc();

    @Query("SELECT COALESCE(SUM(u.storageUsed), 0) FROM User u")
    long sumStorageUsed();

    @Query("SELECT COALESCE(SUM(u.storageQuota), 0) FROM User u")
    long sumStorageQuota();
}
