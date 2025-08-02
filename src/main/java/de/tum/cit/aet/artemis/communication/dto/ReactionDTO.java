package de.tum.cit.aet.artemis.communication.dto;

import java.time.ZonedDateTime;

import de.tum.cit.aet.artemis.communication.domain.Reaction;

public record ReactionDTO(Long id, AuthorDTO user, ZonedDateTime creationDate, String emojiId, Long postId, Long answerPostId) {

    public ReactionDTO(Reaction reaction) {
        this(reaction.getId(), reaction.getUser() != null ? new AuthorDTO(reaction.getUser()) : null, reaction.getCreationDate(), reaction.getEmojiId(),
                reaction.getPost() != null ? reaction.getPost().getId() : null, reaction.getAnswerPost() != null ? reaction.getAnswerPost().getId() : null);
    }
}
