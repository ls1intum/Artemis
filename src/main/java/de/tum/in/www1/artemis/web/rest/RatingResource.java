package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Rating;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.*;

/**
 * REST controller for managing Rating.
 */
@Validated
@RestController
@RequestMapping("/api")
public class RatingResource {

    private static final String ENTITY_NAME = "rating";

    private final Logger log = LoggerFactory.getLogger(RatingResource.class);

    private final RatingService ratingService;

    private final UserRepository userRepository;

    private final AuthorizationCheckService authCheckService;

    private final ResultService resultService;

    private final CourseRepository courseRepository;

    public RatingResource(RatingService ratingService, UserRepository userRepository, AuthorizationCheckService authCheckService, ResultService resultService,
            CourseRepository courseRepository) {
        this.ratingService = ratingService;
        this.userRepository = userRepository;
        this.authCheckService = authCheckService;
        this.resultService = resultService;
        this.courseRepository = courseRepository;
    }

    /**
     * Return Rating referencing resultId or null
     *
     * @param resultId - Id of result that is referenced with the rating
     * @return Rating or null
     */
    @GetMapping("/results/{resultId}/rating")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Optional<Rating>> getRatingForResult(@PathVariable Long resultId) {
        // TODO allow for Instructors
        if (!checkIfUserIsOwnerOfSubmission(resultId) && !authCheckService.isAdmin()) {
            return forbidden();
        }
        Optional<Rating> rating = ratingService.findRatingByResultId(resultId);
        return ResponseEntity.ok(rating);
    }

    /**
     * Persist a new Rating
     *
     * @param resultId    - Id of result that is referenced with the rating that should be persisted
     * @param ratingValue - Value of the updated rating
     * @return inserted Rating
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/results/{resultId}/rating/{ratingValue}")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Rating> createRatingForResult(@PathVariable Long resultId,
            @Valid @PathVariable @Min(value = 1, message = "rating has to be between 1 and 5") @Max(value = 5, message = "rating has to be between 1 and 5") Integer ratingValue)
            throws URISyntaxException {
        if (!checkIfUserIsOwnerOfSubmission(resultId)) {
            return forbidden();
        }

        Rating savedRating = ratingService.saveRating(resultId, ratingValue);
        return ResponseEntity.created(new URI("/api/results/" + savedRating.getId() + "/rating")).body(savedRating);
    }

    /**
     * Update a Rating
     *
     * @param resultId    - Id of result that is referenced with the rating that should be updated
     * @param ratingValue - Value of the updated rating
     * @return updated Rating
     */
    @PutMapping("/results/{resultId}/rating/{ratingValue}")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Rating> updateRatingForResult(@PathVariable Long resultId,
            @Valid @PathVariable @Min(value = 1, message = "rating has to be between 1 and 5") @Max(value = 5, message = "rating has to be between 1 and 5") Integer ratingValue) {
        if (!checkIfUserIsOwnerOfSubmission(resultId)) {
            return forbidden();
        }

        Rating savedRating = ratingService.updateRating(resultId, ratingValue);
        return ResponseEntity.ok(savedRating);
    }

    /**
     * Get all ratings for the "courseId" Course
     *
     * @param courseId - Id of the course that the ratings are fetched for
     * @return List of Ratings for the course
     */
    @GetMapping("/course/{courseId}/rating")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<Rating>> getRatingForInstructorDashboard(@PathVariable Long courseId) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        Course course = courseRepository.findByIdElseThrow(courseId);

        if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
            return forbidden();
        }

        List<Rating> responseRatings = ratingService.getAllRatingsByCourse(courseId);

        return ResponseEntity.ok(responseRatings);
    }

    /**
     * Check if currently logged in user in the owner of the participation
     *
     * @param resultId - Id of the result that the participation belongs to
     * @return False if User is not Owner, True otherwise
     */
    private boolean checkIfUserIsOwnerOfSubmission(Long resultId) {
        User user = userRepository.getUser();
        Result result = resultService.findOne(resultId);
        return authCheckService.isOwnerOfParticipation((StudentParticipation) result.getParticipation(), user);
    }
}
