package de.tum.cit.aet.artemis.assessment.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Map;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.Rating;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.dto.dashboard.ExerciseRatingCountDTO;
import de.tum.cit.aet.artemis.assessment.dto.dashboard.RatingListItemDTO;
import de.tum.cit.aet.artemis.assessment.repository.RatingRepository;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;

/**
 * Service Implementation for managing {@link Rating}.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class RatingService {

    private final RatingRepository ratingRepository;

    private final ResultRepository resultRepository;

    /**
     * Maps DTO field names to JPQL entity paths for sorting.
     * These paths must match the aliases used in the findAllForInstructorDashboard query.
     */
    private static final Map<String, String> SORT_PROPERTY_MAPPING = Map.of("id", "ra.id", "rating", "ra.rating", "assessmentType", "r.assessmentType", "assessorLogin", "a.login",
            "assessorName", "a.firstName", "resultId", "r.id", "submissionId", "s.id", "exerciseTitle", "e.title", "exerciseType", "e.class");

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
     * Get all ratings for the "courseId" Course as paginated DTOs for the instructor dashboard.
     * DTOs are created directly in the database query for optimal performance.
     *
     * @param courseId - id of the course that the ratings are fetched for
     * @param pageable - pagination information
     * @return Page of RatingListItemDTOs for the course
     */
    public Page<RatingListItemDTO> getAllRatingsForDashboard(long courseId, Pageable pageable) {
        Pageable mappedPageable = mapSortProperties(pageable);
        return ratingRepository.findAllForInstructorDashboard(courseId, mappedPageable);
    }

    /**
     * Maps the sort properties in the Pageable from DTO field names to entity paths.
     * This is necessary because the DTO constructor expression in JPQL doesn't create
     * sortable aliases that match the DTO field names.
     *
     * @param pageable the original pageable with DTO field names
     * @return a new pageable with mapped entity paths for sorting
     */
    private Pageable mapSortProperties(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return pageable;
        }

        Sort mappedSort = Sort.by(pageable.getSort().stream().map(order -> {
            String mappedProperty = SORT_PROPERTY_MAPPING.getOrDefault(order.getProperty(), order.getProperty());
            return order.isAscending() ? Sort.Order.asc(mappedProperty) : Sort.Order.desc(mappedProperty);
        }).toList());

        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), mappedSort);
    }

    /**
     * Count all ratings for the "exerciseId" Exercise
     *
     * @param exerciseId - id of the exercise that the ratings are fetched for
     * @return number of ratings for the exercise
     */
    public long countRatingsByExerciseId(long exerciseId) {
        return ratingRepository.countByResult_Submission_Participation_Exercise_Id(exerciseId);
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
    public ExerciseRatingCountDTO averageRatingByExerciseId(Long exerciseId) {
        return ratingRepository.averageRatingByExerciseId(exerciseId);
    }
}
