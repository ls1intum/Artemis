package de.tum.in.www1.artemis.repository;

import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Achievement;
import de.tum.in.www1.artemis.domain.enumeration.AchievementType;

/**
 * Spring Data JPA repository for the Achievement entity.
 */
@Repository
public interface AchievementRepository extends JpaRepository<Achievement, Long> {

    @Query("SELECT a FROM Achievement a WHERE a.course.id = :#{#courseId}")
    Set<Achievement> findAllByCourseId(@Param("courseId") Long courseId);

    @Query("SELECT a FROM Achievement a WHERE a.exercise.id = :#{#exerciseId}")
    Set<Achievement> findAllByExerciseId(@Param("exerciseId") Long exerciseId);

    @Query("SELECT a FROM Achievement a JOIN a.users u WHERE u.id = :#{#userId}")
    Set<Achievement> findAllByUserId(@Param("userId") Long userId);

    @Query("SELECT a FROM Achievement a JOIN a.users u WHERE u.id = :#{#userId} AND a.course.id = :#{#courseId}")
    Set<Achievement> findAllByUserIdAndCourseId(@Param("userId") Long userId, @Param("courseId") Long courseId);

    @Query("SELECT a FROM Achievement a LEFT JOIN FETCH a.users u WHERE a.course.id = :#{#courseId} AND a.type = :#{#type}")
    Set<Achievement> findAllForRewardedTypeInCourse(@Param("courseId") Long courseId, @Param("type") AchievementType type);

    @Query("SELECT a FROM Achievement a LEFT JOIN FETCH a.users u WHERE a.course.id = :#{#courseId} AND a.exercise.id = :#{#exerciseId} AND a.type = :#{#type}")
    Set<Achievement> findAllForRewardedTypeInExercise(@Param("courseId") Long courseId, @Param("exerciseId") Long exerciseId, @Param("type") AchievementType type);

    void deleteByCourse_Id(Long courseId);

}
