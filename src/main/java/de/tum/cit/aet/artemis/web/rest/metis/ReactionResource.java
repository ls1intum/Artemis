package de.tum.cit.aet.artemis.web.rest.metis;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.domain.metis.Reaction;
import de.tum.cit.aet.artemis.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.service.metis.ReactionService;

/**
 * REST controller for Reaction on Postings.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class ReactionResource {

    private static final Logger log = LoggerFactory.getLogger(ReactionResource.class);

    private final ReactionService reactionService;

    public ReactionResource(ReactionService reactionService) {
        this.reactionService = reactionService;
    }

    /**
     * POST /courses/{courseId}/posts/{id}/reactions : Create a new reaction on a posting
     *
     * @param courseId id of the course the posting that is reacted on belongs to
     * @param reaction reaction to create
     * @return ResponseEntity with status 201 (Created) containing the created reaction in the response body,
     *         or with status 400 (Bad Request) if the checks on user, course or posting validity fail
     */
    @PostMapping("courses/{courseId}/postings/reactions")
    @EnforceAtLeastStudent
    public ResponseEntity<Reaction> createReaction(@PathVariable Long courseId, @Valid @RequestBody Reaction reaction) throws URISyntaxException {
        try {
            Reaction createdReaction = reactionService.createReaction(courseId, reaction);
            return ResponseEntity.created(new URI("/api/courses/" + courseId + "/postings/reactions/" + createdReaction.getId())).body(createdReaction);
        }
        catch (DataIntegrityViolationException ex) {
            // this error can occur when multiple reactions are created at the exact same time, we log it, but doe not send it to the client
            log.warn(ex.getMessage(), ex);
            return ResponseEntity.ok(null);
        }
    }

    /**
     * DELETE /courses/{courseId}/posts/{id}/reactions/{reactionId} : Delete a reaction by its id
     *
     * @param courseId   id of the course the posting that is reacted on belongs to
     * @param reactionId id of the reaction to delete
     * @return ResponseEntity with status 200 (OK),
     *         or 400 (Bad Request) if the checks on user, course or posting validity fail
     */
    @DeleteMapping("courses/{courseId}/postings/reactions/{reactionId}")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> deleteReaction(@PathVariable Long courseId, @PathVariable Long reactionId) throws URISyntaxException {
        reactionService.deleteReactionById(reactionId, courseId);
        return ResponseEntity.ok().build();
    }
}
