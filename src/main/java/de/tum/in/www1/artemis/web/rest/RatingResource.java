package de.tum.in.www1.artemis.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Rating;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.RatingService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

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

    private final ResultRepository resultRepository;

    private final CourseRepository courseRepository;

    public RatingResource(RatingService ratingService, UserRepository userRepository, AuthorizationCheckService authCheckService, ResultRepository resultRepository,
            CourseRepository courseRepository) {
        this.ratingService = ratingService;
        this.userRepository = userRepository;
        this.authCheckService = authCheckService;
        this.resultRepository = resultRepository;
        this.courseRepository = courseRepository;
    }

    /**
     * Return Rating referencing resultId or null
     *
     * @param resultId - Id of result that is referenced with the rating
     * @return Rating or null
     */
    @GetMapping("/results/{resultId}/rating")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Optional<Rating>> getRatingForResult(@PathVariable Long resultId) {
        // TODO allow for Instructors
        if (!authCheckService.isAdmin()) {
            checkIfUserIsOwnerOfSubmissionElseThrow(resultId);
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
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Rating> createRatingForResult(@PathVariable long resultId, @PathVariable int ratingValue) throws URISyntaxException {
        checkRating(ratingValue);
        checkIfUserIsOwnerOfSubmissionElseThrow(resultId);
        Rating savedRating = ratingService.saveRating(resultId, ratingValue);
        return ResponseEntity.created(new URI("/api/results/" + savedRating.getId() + "/rating")).body(savedRating);
    }

    private void checkRating(int ratingValue) {
        if (ratingValue < 1 || ratingValue > 5) {
            throw new BadRequestAlertException("rating has to be between 1 and 5", ENTITY_NAME, "ratingValue.invalid", false);
        }
    }

    /**
     * Update a Rating
     *
     * @param resultId    - Id of result that is referenced with the rating that should be updated
     * @param ratingValue - Value of the updated rating
     * @return updated Rating
     */
    @PutMapping("/results/{resultId}/rating/{ratingValue}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Rating> updateRatingForResult(@PathVariable long resultId, @PathVariable int ratingValue) {
        checkRating(ratingValue);
        checkIfUserIsOwnerOfSubmissionElseThrow(resultId);
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
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<Rating>> getRatingForInstructorDashboard(@PathVariable Long courseId) {
        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        List<Rating> responseRatings = ratingService.getAllRatingsByCourse(courseId);
        return ResponseEntity.ok(responseRatings);
    }

    /**
     * Check if currently logged-in user in the owner of the participation, if this is not the case throw an AccessForbiddenException
     *
     * @param resultId - Id of the result that the participation belongs to
     */
    private void checkIfUserIsOwnerOfSubmissionElseThrow(Long resultId) {
        User user = userRepository.getUser();
        Result result = resultRepository.findByIdElseThrow(resultId);
        if (!authCheckService.isOwnerOfParticipation((StudentParticipation) result.getParticipation(), user)) {
            log.warn("User {} has tried to access rating for result {}", user.getLogin(), resultId);
            throw new AccessForbiddenException();
        }
    }
}
