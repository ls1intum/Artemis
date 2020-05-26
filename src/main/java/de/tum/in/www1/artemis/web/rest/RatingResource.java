package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import de.tum.in.www1.artemis.domain.Rating;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.service.RatingService;
import de.tum.in.www1.artemis.service.ResultService;
import de.tum.in.www1.artemis.service.UserService;

/**
 * REST controller for managing Rating.
 */
@RestController
@RequestMapping("/api")
public class RatingResource {

    private static final String ENTITY_NAME = "rating";

    private final Logger log = LoggerFactory.getLogger(RatingResource.class);

    private final RatingService ratingService;

    private final UserService userService;

    private final AuthorizationCheckService authCheckService;

    private final ResultService resultService;

    private final ParticipationService participationService;

    public RatingResource(RatingService ratingService, UserService userService, AuthorizationCheckService authCheckService, ResultService resultService,
            ParticipationService participationService) {
        this.ratingService = ratingService;
        this.userService = userService;
        this.authCheckService = authCheckService;
        this.resultService = resultService;
        this.participationService = participationService;
    }

    /**
     * Return Rating referencing resultId or 404 Not Found
     *
     * @param resultId - Id of result that is referenced with the rating
     * @return Rating or 404 Not Found
     */
    @GetMapping("/results/{resultId}/rating")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Rating> getRatingForResult(@PathVariable Long resultId) {
        Optional<Rating> rating = this.ratingService.findRatingByResultId(resultId);
        if (rating.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Rating does not exist!");
        }
        return ResponseEntity.ok(rating.get());
    }

    /**
     * Persist a new Rating
     *
     * @param resultId    - Id of the rating that should be updated
     * @param ratingValue - Value of the updated rating
     * @return inserted Rating
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/results/{resultId}/rating/{ratingValue}")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Rating> createRatingForResult(@PathVariable Long resultId, @PathVariable Integer ratingValue) throws URISyntaxException {
        User user = userService.getUser();
        Result res = resultService.findOne(resultId);
        StudentParticipation participation = participationService.findOneStudentParticipation(res.getParticipation().getId());
        if (!authCheckService.isOwnerOfParticipation(participation, user)) {
            return forbidden();
        }

        Rating savedRating = ratingService.saveRating(resultId, ratingValue);
        return ResponseEntity.created(new URI("/api/results/" + savedRating.getId() + "/rating")).body(savedRating);
    }

    /**
     * Update a Rating
     *
     * @param resultId    - Id of the rating that should be updated
     * @param ratingValue - Value of the updated rating
     * @return updated Rating
     */
    @PutMapping("/results/{resultId}/rating/{ratingValue}")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Rating> updateRatingForResult(@PathVariable Long resultId, @PathVariable Integer ratingValue) {
        User user = userService.getUser();
        Result res = resultService.findOne(resultId);
        StudentParticipation participation = participationService.findOneStudentParticipation(res.getParticipation().getId());
        if (!authCheckService.isOwnerOfParticipation(participation, user)) {
            return forbidden();
        }

        Rating savedRating = ratingService.updateRating(resultId, ratingValue);
        return ResponseEntity.ok(savedRating);
    }
}
