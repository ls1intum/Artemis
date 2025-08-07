package de.tum.cit.aet.artemis.communication.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.Reaction;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ReactionDTO(Long id, AuthorDTO user, ZonedDateTime creationDate, String emojiId, long relatedPostId) {

    /**
     * Maps a Reaction entity to a ReactionDTO for data transfer to clients.
     *
     * @param reaction the Reaction entity to map from
     */
    public ReactionDTO(Reaction reaction) {
        this(reaction.getId(), AuthorDTO.fromUser(reaction.getUser()), reaction.getCreationDate(), reaction.getEmojiId(),
                reaction.getPost() != null ? reaction.getPost().getId() : reaction.getAnswerPost().getId());
    }
}
