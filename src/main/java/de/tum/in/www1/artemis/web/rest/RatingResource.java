package de.tum.in.www1.artemis.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import de.tum.in.www1.artemis.domain.Rating;
import de.tum.in.www1.artemis.service.RatingService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing Rating.
 */
@RestController
@RequestMapping("/api")
public class RatingResource {

    private static final String ENTITY_NAME = "rating";

    private final Logger log = LoggerFactory.getLogger(RatingResource.class);

    private final RatingService ratingService;

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    @Value("${artemis.continuous-integration.artemis-authentication-token-value}")
    private String ARTEMIS_AUTHENTICATION_TOKEN_VALUE = "";

    public RatingResource(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    /**
     * Return Rating referencing resultId or null
     *
     * @param resultId - Id of result that is referenced with the rating
     * @return Rating or null
     */
    @GetMapping("/rating/result/{resultId}")
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
     * @param rating - Rating that should be persisted
     * @return updated Rating
     */
    @PostMapping("/rating")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Rating> createRatingForResult(@RequestBody Rating rating) throws URISyntaxException {
        // TODO: Check authorization
        Rating result = this.ratingService.saveRating(rating);
        return ResponseEntity.created(new URI("/api/rating/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.getId().toString())).body(result);
    }

    /**
     * Update a Rating
     *
     * @param rating - updated Rating
     * @return updated Rating
     */
    @PutMapping("/rating")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Rating> updateRatingForResult(@RequestBody Rating rating) {
        if (rating.getId() == null) {
            throw new BadRequestAlertException("The rating must have an ID", ENTITY_NAME, "idDoesNotExist");
        }
        // TODO: Check authorization
        Rating result = this.ratingService.updateRating(rating);
        return ResponseEntity.ok(result);
    }
}
