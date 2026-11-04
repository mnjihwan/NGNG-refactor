package com.ngng.api.user.point.repository;

import com.ngng.api.user.point.entity.PointHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PointHistoryRepository extends JpaRepository<PointHistory,Long> {

    @Query("SELECT p FROM PointHistory p WHERE p.user.id = :userId ORDER BY id DESC LIMIT 1" )
    Optional<PointHistory> findLastByUserId(@Param("userId") Long userId);

    @Query("SELECT p FROM PointHistory p WHERE p.id IN " +
            "(SELECT MAX(h.id) FROM PointHistory h WHERE h.user.userId IN :userIds GROUP BY h.user.userId)")
    List<PointHistory> findLatestByUserIds(@Param("userIds") List<Long> userIds);

    @Query("SELECT p FROM PointHistory p WHERE p.user.id = :userId ORDER BY id DESC" )
    List<PointHistory> findAllByUserId(@Param("userId") Long userId);

}
