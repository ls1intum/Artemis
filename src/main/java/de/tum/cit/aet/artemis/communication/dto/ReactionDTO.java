package de.tum.cit.aet.artemis.communication.dto;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.PostingType;
import de.tum.cit.aet.artemis.communication.domain.Reaction;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;

/**
 * A reaction on a posting.
 *
 * <p>
 * {@code relatedPostId} does not identify a posting on its own: {@link de.tum.cit.aet.artemis.communication.domain.Post} and
 * {@link de.tum.cit.aet.artemis.communication.domain.AnswerPost} are numbered independently, so the same value regularly exists in both. Clients
 * send {@code postingType} to say which one they mean. It is optional so that clients released before it existed keep working, and the server
 * falls back to resolving the id within the course.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ReactionDTO(Long id, AuthorDTO user, ZonedDateTime creationDate, @NotBlank String emojiId, @Positive long relatedPostId, PostingType postingType) {

    /**
     * Maps a Reaction entity to a ReactionDTO for data transfer to clients.
     *
     * @param reaction the Reaction entity to map from
     */
    public ReactionDTO(Reaction reaction) {
        this(reaction.getId(), AuthorDTO.fromUser(reaction.getUser()), reaction.getCreationDate(), reaction.getEmojiId(), relatedPostIdOrThrow(reaction),
                reaction.getAnswerPost() != null ? PostingType.ANSWER : PostingType.POST);
    }

    /**
     * Determines the related post-ID for the reaction
     *
     * @param reaction the Reaction entity
     * @return the ID of the associated Post or AnswerPost
     * @throws BadRequestAlertException if neither association exists
     */
    private static long relatedPostIdOrThrow(Reaction reaction) {
        if (reaction.getPost() != null) {
            return reaction.getPost().getId();
        }
        if (reaction.getAnswerPost() != null) {
            return reaction.getAnswerPost().getId();
        }
        throw new BadRequestAlertException("Reaction must be associated with a Post or AnswerPost.", "reaction", "missingAssociation");
    }
}
