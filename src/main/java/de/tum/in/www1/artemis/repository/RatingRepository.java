package de.tum.in.www1.artemis.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Rating;

/**
 * Spring Data JPA repository for the Rating entity.
 */
@Repository
public interface RatingRepository extends JpaRepository<Rating, Long> {

    Optional<Rating> findRatingByResultId(Long resultId);

    /**
     * Delete all ratings that belong to results of a given participation
     * @param participationId the Id of the participation where the ratings should be deleted
     */
    void deleteByResult_Participation_Id(Long participationId);

    /**
     * Delete all ratings that belong to the given result
     * @param resultId the Id of the result where the rating should be deleted
     */
    void deleteByResult_Id(long resultId);

    List<Rating> findAllByResult_Participation_Exercise_Course_Id(Long courseId);

    /**
     * Count all ratings given to submissions for the given course.
     * @param courseId the id of the course where for which ratings are counted
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
