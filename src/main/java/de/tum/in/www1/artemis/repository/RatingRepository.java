package de.tum.in.www1.artemis.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.Rating;
import de.tum.in.www1.artemis.domain.assessment.dashboard.ExerciseRatingCount;

/**
 * Spring Data JPA repository for the Rating entity.
 */
@Repository
public interface RatingRepository extends JpaRepository<Rating, Long> {

    Optional<Rating> findRatingByResultId(Long resultId);

    /**
     * Delete all ratings that belong to the given result
     * @param resultId the id of the result where the rating should be deleted
     */
    @Transactional // ok because of delete
    @Modifying
    void deleteByResult_Id(long resultId);

    List<Rating> findAllByResult_Participation_Exercise_Course_Id(Long courseId);

    @Query("""
                SELECT new de.tum.in.www1.artemis.domain.assessment.dashboard.ExerciseRatingCount(
                    cast(sum(ra.rating) as double) / sum(case when ra.rating is not null then 1 else 0 end),
                    sum(case when ra.rating is not null then 1 else 0 end))
                FROM
                    Result r JOIN r.participation p JOIN p.exercise e
                    LEFT JOIN FETCH Rating ra ON ra.result = r.id
                WHERE
                    r.completionDate is not null AND
                    e.id = :#{#exerciseId}
            """)
    ExerciseRatingCount averageRatingByExerciseId(@Param("exerciseId") Long exerciseId);

    /**
     * Count all ratings given to submissions for the given course.
     * @param courseId the id of the course for which the ratings are counted
     * @return number of total ratings given for the course
     */
    long countByResult_Participation_Exercise_Course_Id(Long courseId);

    /**
     * Count all ratings given to assessments for the given exercise.
     * @param exerciseId the id of the exercise for which the ratings are counted
     * @return number of total ratings given for the exercise
     */
    long countByResult_Participation_Exercise_Id(Long exerciseId);
}
