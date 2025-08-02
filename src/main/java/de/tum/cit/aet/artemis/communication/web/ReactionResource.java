package de.tum.cit.aet.artemis.communication.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.communication.domain.Reaction;
import de.tum.cit.aet.artemis.communication.dto.ReactionDTO;
import de.tum.cit.aet.artemis.communication.service.ReactionService;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;

/**
 * REST controller for Reaction on Postings.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/communication/")
public class ReactionResource {

    private static final Logger log = LoggerFactory.getLogger(ReactionResource.class);

    private final ReactionService reactionService;

    public ReactionResource(ReactionService reactionService) {
        this.reactionService = reactionService;
    }

    /**
     * POST /courses/{courseId}/postings/reactions : Create a new reaction on a posting
     *
     * @param courseId    id of the course the posting that is reacted on belongs to
     * @param reactionDto reaction to create
     * @return a 201 (Created) with the created ReactionDTO in the body,
     *         or 200 (OK) if an identical reaction was already present,
     *         or 400 (Bad Request) if validation of the DTO, courseId or postingId fails
     */
    @PostMapping("courses/{courseId}/postings/reactions")
    @EnforceAtLeastStudent
    public ResponseEntity<ReactionDTO> createReaction(@PathVariable Long courseId, @Valid @RequestBody ReactionDTO reactionDto) throws URISyntaxException {
        try {
            Reaction createdReaction = reactionService.createReaction(courseId, reactionDto);
            URI location = new URI("/api/communication/courses/" + courseId + "/postings/reactions/" + createdReaction.getId());
            return ResponseEntity.created(location).body(new ReactionDTO(createdReaction));
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
    public ResponseEntity<Void> deleteReaction(@PathVariable long courseId, @PathVariable long reactionId) {
        // NOTE: the service method handles authorization checks and throws an exception if the user is not allowed to delete the reaction
        reactionService.deleteReactionByIdIfAllowedElseThrow(reactionId, courseId);
        return ResponseEntity.ok().build();
    }
}
