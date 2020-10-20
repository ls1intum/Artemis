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

    @Query("SELECT a FROM Achievement a LEFT JOIN FETCH a.users u LEFT JOIN FETCH u.achievements WHERE a.course.id = :#{#courseId}")
    Set<Achievement> findAllByCourseId(@Param("courseId") Long courseId);

    @Query("SELECT a FROM Achievement a JOIN a.users u WHERE u.id = :#{#userId} AND a.course.id = :#{#courseId}")
    Set<Achievement> findAllByUserIdAndCourseId(@Param("userId") Long userId, @Param("courseId") Long courseId);
}
