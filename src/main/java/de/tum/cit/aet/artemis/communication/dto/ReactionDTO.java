package de.tum.cit.aet.artemis.communication.dto;

import java.time.ZonedDateTime;

import de.tum.cit.aet.artemis.communication.domain.Reaction;

public record ReactionDTO(Long id, AuthorDTO user, ZonedDateTime creationDate, String emojiId) {

    public ReactionDTO(Reaction reaction) {
        this(reaction.getId(), new AuthorDTO(reaction.getUser()), reaction.getCreationDate(), reaction.getEmojiId());
    }
}
