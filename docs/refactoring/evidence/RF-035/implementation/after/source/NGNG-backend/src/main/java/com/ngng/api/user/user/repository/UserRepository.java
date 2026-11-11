package com.ngng.api.user.user.repository;

import com.ngng.api.user.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // Lock only the stable user row, without fetching/locking the eager role association.
    @Query(value = "SELECT user_id FROM `user` WHERE user_id = :userId FOR UPDATE", nativeQuery = true)
    Optional<Long> lockUserIdForPointUpdate(@Param("userId") Long userId);

    Optional<User> findUserByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByPhoneNumber(String PhoneNumber);
    boolean existsByAccountNumber(String accountNumber);

    Page<User> findByVisible(Boolean visible, Pageable pageable);
}
