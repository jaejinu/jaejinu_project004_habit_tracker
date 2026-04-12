package com.habit.domain.badge;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SharedBadgeRepository extends JpaRepository<SharedBadge, Long> {

    Optional<SharedBadge> findByPublicToken(String token);

    List<SharedBadge> findAllByHabitId(Long habitId);

    @Modifying
    @Query("UPDATE SharedBadge s SET s.viewCount = s.viewCount + 1 WHERE s.publicToken = :token")
    int incrementViewCount(@Param("token") String token);
}
