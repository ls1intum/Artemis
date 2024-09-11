package de.tum.cit.aet.artemis.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.domain.Rating;
import de.tum.cit.aet.artemis.domain.assessment.dashboard.ExerciseRatingCount;
import de.tum.cit.aet.artemis.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the Rating entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface RatingRepository extends ArtemisJpaRepository<Rating, Long> {

    Optional<Rating> findRatingByResultId(long resultId);

    /**
     * Delete all ratings that belong to the given result
     *
     * @param resultId the id of the result where the rating should be deleted
     */
    @Transactional // ok because of delete
    @Modifying
    void deleteByResult_Id(long resultId);

    List<Rating> findAllByResult_Participation_Exercise_Course_Id(Long courseId);

    // Valid JPQL syntax, only SCA is not able to parse it
    @Query("""
                SELECT new de.tum.cit.aet.artemis.domain.assessment.dashboard.ExerciseRatingCount(
                    CAST(SUM(ra.rating) AS double) / SUM(CASE WHEN ra.rating IS NOT NULL THEN 1 ELSE 0 END),
                    SUM(CASE WHEN ra.rating IS NOT NULL THEN 1 ELSE 0 END))
                FROM Result r
                    JOIN r.participation p
                    JOIN p.exercise e
                    LEFT JOIN FETCH Rating ra ON ra.result = r
                WHERE r.completionDate IS NOT NULL
                    AND e.id = :exerciseId
            """)
    ExerciseRatingCount averageRatingByExerciseId(@Param("exerciseId") long exerciseId);

    /**
     * Count all ratings given to submissions for the given course.
     *
     * @param courseId the id of the course for which the ratings are counted
     * @return number of total ratings given for the course
     */
    long countByResult_Participation_Exercise_Course_Id(long courseId);

    /**
     * Count all ratings given to assessments for the given exercise.
     *
     * @param exerciseId the id of the exercise for which the ratings are counted
     * @return number of total ratings given for the exercise
     */
    long countByResult_Participation_Exercise_Id(long exerciseId);
}
