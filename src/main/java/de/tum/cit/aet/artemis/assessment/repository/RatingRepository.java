package de.tum.cit.aet.artemis.assessment.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.assessment.domain.Rating;
import de.tum.cit.aet.artemis.assessment.dto.dashboard.ExerciseRatingCountDTO;
import de.tum.cit.aet.artemis.assessment.dto.dashboard.RatingListItemDTO;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the Rating entity.
 */
@Profile(PROFILE_CORE)
@Lazy
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

    /**
     * Deletes ratings associated with the supplied results.
     * <p>
     * <b>Precondition:</b> {@code resultIds} is non-{@code null}, non-empty, and contains persisted result ids.
     * <p>
     * <b>Postcondition:</b> no rating references one of the supplied results.
     *
     * @param resultIds ids of the results whose ratings are deleted
     */
    @Transactional // ok because of delete
    @Modifying
    @Query("DELETE FROM Rating rating WHERE rating.result.id IN :resultIds")
    void deleteByResultIds(@Param("resultIds") final Collection<Long> resultIds);

    /**
     * Find all ratings for a course as DTOs for the instructor dashboard with pagination.
     * This query uses a constructor expression to create DTOs directly in the database query,
     * minimizing data transfer by selecting only the fields needed for the rating list view.
     *
     * @param courseId the id of the course for which ratings are fetched
     * @param pageable pagination information
     * @return page of RatingListItemDTOs containing only the data needed for the dashboard
     */
    @Query("""
                SELECT new de.tum.cit.aet.artemis.assessment.dto.dashboard.RatingListItemDTO(
                    ra.id,
                    ra.rating,
                    r.assessmentType,
                    a.login,
                    CONCAT(a.firstName, ' ', a.lastName),
                    r.id,
                    s.id,
                    p.id,
                    e.id,
                    e.title,
                    TYPE(e)
                )
                FROM Rating ra
                    JOIN ra.result r
                    JOIN r.submission s
                    JOIN s.participation p
                    JOIN p.exercise e
                    LEFT JOIN r.assessor a
                WHERE e.course.id = :courseId
            """)
    Page<RatingListItemDTO> findAllForInstructorDashboard(@Param("courseId") Long courseId, Pageable pageable);

    // Valid JPQL syntax, only SCA is not able to parse it
    @Query("""
                SELECT new de.tum.cit.aet.artemis.assessment.dto.dashboard.ExerciseRatingCountDTO(
                    CAST(CAST(SUM(ra.rating) AS double) / SUM(CASE WHEN ra.rating IS NOT NULL THEN 1 ELSE 0 END) AS double),
                    SUM(CASE WHEN ra.rating IS NOT NULL THEN 1 ELSE 0 END))
                FROM Result r
                    JOIN r.submission s
                    JOIN s.participation p
                    JOIN p.exercise e
                    LEFT JOIN FETCH Rating ra ON ra.result = r
                WHERE r.completionDate IS NOT NULL
                    AND e.id = :exerciseId
            """)
    ExerciseRatingCountDTO averageRatingByExerciseId(@Param("exerciseId") long exerciseId);

    /**
     * Count all ratings given to submissions/assessments for the given exercises. Uses the denormalized
     * {@code result.exerciseId} column to avoid joining through submission → participation → exercise → course.
     *
     * @param exerciseIds the ids of the exercises for which the ratings are counted
     * @return number of total ratings given for the exercises
     */
    @Query("""
            SELECT COUNT(r)
            FROM Rating r
            WHERE r.result.exerciseId IN :exerciseIds
            """)
    long countByResultExerciseIds(@Param("exerciseIds") Set<Long> exerciseIds);

    /**
     * Count all ratings given to assessments for the given exercise.
     *
     * @param exerciseId the id of the exercise for which the ratings are counted
     * @return number of total ratings given for the exercise
     */
    long countByResult_Submission_Participation_Exercise_Id(long exerciseId);
}
