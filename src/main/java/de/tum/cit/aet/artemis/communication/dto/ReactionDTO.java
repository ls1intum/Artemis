package de.tum.cit.aet.artemis.communication.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.Reaction;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ReactionDTO(Long id, AuthorDTO user, ZonedDateTime creationDate, String emojiId, Long relatedPostId) {

    /**
     * Maps a Reaction entity to a ReactionDTO for data transfer to clients.
     *
     * @param reaction the Reaction entity to map from
     */
    public ReactionDTO(Reaction reaction) {
        this(reaction.getId(), AuthorDTO.fromUser(reaction.getUser()), reaction.getCreationDate(), reaction.getEmojiId(), getRelatedPostId(reaction));
    }

    /**
     * Determines the related post ID for the reaction, ensuring null safety.
     *
     * @param reaction the Reaction entity
     * @return the ID of the associated Post or AnswerPost
     * @throws BadRequestAlertException if neither association exists
     */
    static Long getRelatedPostId(Reaction reaction) {
        if (reaction.getPost() != null) {
            return reaction.getPost().getId();
        }
        if (reaction.getAnswerPost() != null) {
            return reaction.getAnswerPost().getId();
        }
        throw new BadRequestAlertException("The reaction could not be found.", "reaction", "notFound");

    }
}
