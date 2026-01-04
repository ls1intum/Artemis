package de.tum.cit.aet.artemis.assessment.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.tum.cit.aet.artemis.assessment.domain.Rating;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.dto.dashboard.RatingListItemDTO;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.service.RatingService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastInstructorInCourse;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import tech.jhipster.web.util.PaginationUtil;

/**
 * REST controller for managing Rating.
 */
@Validated
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/assessment/")
public class RatingResource {

    private static final String ENTITY_NAME = "rating";

    private static final Logger log = LoggerFactory.getLogger(RatingResource.class);

    private final RatingService ratingService;

    private final UserRepository userRepository;

    private final AuthorizationCheckService authCheckService;

    private final ResultRepository resultRepository;

    public RatingResource(RatingService ratingService, UserRepository userRepository, AuthorizationCheckService authCheckService, ResultRepository resultRepository) {
        this.ratingService = ratingService;
        this.userRepository = userRepository;
        this.authCheckService = authCheckService;
        this.resultRepository = resultRepository;
    }

    /**
     * GET /results/:resultId/rating : Return Rating referencing resultId or null
     *
     * @param resultId - Id of result that is referenced with the rating
     * @return saved star rating value or empty optional
     */
    @GetMapping("results/{resultId}/rating")
    @EnforceAtLeastStudent
    public ResponseEntity<Optional<Integer>> getRatingForResult(@PathVariable Long resultId) {
        if (!authCheckService.isAdmin()) {
            checkIfUserIsOwnerOfSubmissionElseThrow(resultId);
        }
        Optional<Rating> rating = ratingService.findRatingByResultId(resultId);
        return ResponseEntity.ok(rating.map(Rating::getRating));
    }

    /**
     * POST /results/:resultId/rating/:ratingValue : Persist a new Rating
     *
     * @param resultId    - Id of result that is referenced with the rating that should be persisted
     * @param ratingValue - Value of the updated rating
     * @return inserted star rating value
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("results/{resultId}/rating/{ratingValue}")
    @EnforceAtLeastStudent
    public ResponseEntity<Integer> createRatingForResult(@PathVariable long resultId, @PathVariable int ratingValue) throws URISyntaxException {
        checkRating(ratingValue);
        checkIfUserIsOwnerOfSubmissionElseThrow(resultId);
        Rating savedRating = ratingService.saveRating(resultId, ratingValue);
        return ResponseEntity.created(new URI("/api/results/" + savedRating.getId() + "/rating")).body(savedRating.getRating());
    }

    private void checkRating(int ratingValue) {
        if (ratingValue < 1 || ratingValue > 5) {
            throw new BadRequestAlertException("rating has to be between 1 and 5", ENTITY_NAME, "ratingValue.invalid", false);
        }
    }

    /**
     * PUT /results/:resultId/rating/:ratingValue : Update a Rating
     *
     * @param resultId    - Id of result that is referenced with the rating that should be updated
     * @param ratingValue - Value of the updated rating
     * @return updated star rating value
     */
    @PutMapping("results/{resultId}/rating/{ratingValue}")
    @EnforceAtLeastStudent
    public ResponseEntity<Integer> updateRatingForResult(@PathVariable long resultId, @PathVariable int ratingValue) {
        checkRating(ratingValue);
        checkIfUserIsOwnerOfSubmissionElseThrow(resultId);
        Rating savedRating = ratingService.updateRating(resultId, ratingValue);
        return ResponseEntity.ok(savedRating.getRating());
    }

    /**
     * GET /course/:courseId/rating : Get paginated ratings for the "courseId" Course
     *
     * @param courseId - Id of the course that the ratings are fetched for
     * @param pageable - Pagination information (page, size, sort)
     * @return List of RatingListItemDTO with pagination info in headers
     */
    @GetMapping("course/{courseId}/rating")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<List<RatingListItemDTO>> getRatingForInstructorDashboard(@PathVariable Long courseId, Pageable pageable) {
        Page<RatingListItemDTO> ratings = ratingService.getAllRatingsForDashboard(courseId, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), ratings);
        return new ResponseEntity<>(ratings.getContent(), headers, HttpStatus.OK);
    }

    /**
     * Check if currently logged-in user in the owner of the participation, if this is not the case throw an AccessForbiddenException
     *
     * @param resultId - Id of the result that the participation belongs to
     */
    private void checkIfUserIsOwnerOfSubmissionElseThrow(Long resultId) {
        User user = userRepository.getUser();
        Result result = resultRepository.findByIdElseThrow(resultId);
        if (!authCheckService.isOwnerOfParticipation((StudentParticipation) result.getSubmission().getParticipation(), user)) {
            log.warn("User {} has tried to access rating for result {}", user.getLogin(), resultId);
            throw new AccessForbiddenException();
        }
    }
}
