package de.tum.in.www1.artemis.web.rest.metis;

import java.net.URI;
import java.net.URISyntaxException;

import javax.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.metis.Reaction;
import de.tum.in.www1.artemis.service.metis.ReactionService;

/**
 * REST controller for Reaction on Postings.
 */
@RestController
public class ReactionResource {

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
     * or with status 400 (Bad Request) if the checks on user, course or posting validity fail
     */
    @PostMapping("courses/{courseId}/postings/reactions")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Reaction> createReaction(@PathVariable Long courseId, @Valid @RequestBody Reaction reaction) throws URISyntaxException {
        Reaction createdReaction = reactionService.createReaction(courseId, reaction);
        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/postings/reactions/" + createdReaction.getId())).body(createdReaction);
    }

    /**
     * DELETE /courses/{courseId}/posts/{id}/reactions/{reactionId} : Delete a reaction by its id
     *
     * @param courseId   id of the course the posting that is reacted on belongs to
     * @param reactionId id of the reaction to delete
     * @return ResponseEntity with status 200 (OK),
     * or 400 (Bad Request) if the checks on user, course or posting validity fail
     */
    @DeleteMapping("courses/{courseId}/postings/reactions/{reactionId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> deleteReaction(@PathVariable Long courseId, @PathVariable Long reactionId) throws URISyntaxException {
        reactionService.deleteReactionById(reactionId, courseId);
        return ResponseEntity.ok().build();
    }
}
