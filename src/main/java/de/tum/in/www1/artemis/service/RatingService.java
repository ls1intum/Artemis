package de.tum.in.www1.artemis.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Rating;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.assessment.dashboard.ExerciseRatingCount;
import de.tum.in.www1.artemis.repository.RatingRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;

/**
 * Service Implementation for managing {@link de.tum.in.www1.artemis.domain.Rating}.
 */
@Service
public class RatingService {

    private final RatingRepository ratingRepository;

    private final ResultRepository resultRepository;

    public RatingService(RatingRepository ratingRepository, ResultRepository resultRepository) {
        this.ratingRepository = ratingRepository;
        this.resultRepository = resultRepository;
    }

    /**
     * Return Rating that refers to Result with id resultId
     *
     * @param resultId - id of Result that the rating refers to
     * @return Rating if it exists else null
     */
    public Optional<Rating> findRatingByResultId(long resultId) {
        return ratingRepository.findRatingByResultId(resultId);
    }

    /**
     * Get all ratings for the "courseId" Course
     * @param courseId - id of the course that the ratings are fetched for
     * @return List of Ratings for the course
     */
    public List<Rating> getAllRatingsByCourse(long courseId) {
        return ratingRepository.findAllByResult_Participation_Exercise_Course_Id(courseId);
    }

    /**
     * Count all ratings for the "exerciseId" Exercise
     * @param exerciseId - id of the exercise that the ratings are fetched for
     * @return number of ratings for the exercise
     */
    public long countRatingsByExerciseId(long exerciseId) {
        return ratingRepository.countByResult_Participation_Exercise_Id(exerciseId);
    }

    /**
     * Persist a new Rating
     *
     * @param resultId    - id of the rating that should be persisted
     * @param ratingValue - Value of the rating that should be persisted
     * @return persisted Rating
     */
    public Rating saveRating(long resultId, int ratingValue) {
        Result result = resultRepository.findById(resultId).orElseThrow();
        Rating serverRating = new Rating();
        serverRating.setRating(ratingValue);
        serverRating.setResult(result);
        return ratingRepository.save(serverRating);
    }

    /**
     * Update an existing Rating
     *
     * @param resultId    - id of the rating that should be updated
     * @param ratingValue - Value of the updated rating
     * @return updated rating
     */
    public Rating updateRating(long resultId, int ratingValue) {
        Rating updatedRating = this.ratingRepository.findRatingByResultId(resultId).orElseThrow();
        updatedRating.setRating(ratingValue);
        return ratingRepository.save(updatedRating);
    }

    /**
     * Computes rating information for the given exercise.
     *
     * @param exerciseId - id of the exercise
     * @return the rating information of the exercise
     */
    public ExerciseRatingCount averageRatingByExerciseId(Long exerciseId) {
        return ratingRepository.averageRatingByExerciseId(exerciseId);
    }
}
