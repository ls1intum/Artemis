package de.tum.in.www1.artemis.repository;

import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Achievement;

/**
 * Spring Data JPA repository for the Achievement entity.
 */
@Repository
public interface AchievementRepository extends JpaRepository<Achievement, Long> {

    @Query("SELECT a FROM Achievement a WHERE a.course.id = :#{#courseId}")
    Set<Achievement> getAllByCourseId(@Param("courseId") Long courseId);

    @Query("SELECT a FROM Achievement a JOIN a.users u WHERE u.id = :#{#userId}")
    Set<Achievement> getAllByUserId(@Param("userId") Long userId);
}
