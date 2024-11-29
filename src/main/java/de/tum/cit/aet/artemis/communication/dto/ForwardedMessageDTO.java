package de.tum.cit.aet.artemis.communication.dto;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.ForwardedMessage;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.PostingType;

/**
 * Data Transfer Object for ForwardedMessage.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ForwardedMessageDTO(Long id, Long sourceId, PostingType sourceType, Long destinationPostId, Long destinationAnswerPostId) {

    /**
     * Constructs a ForwardedMessageDTO from a ForwardedMessage entity.
     *
     * @param message the ForwardedMessage entity
     */
    public ForwardedMessageDTO(ForwardedMessage message) {
        this(message.getId(), message.getSourceId(), message.getSourceType(), Optional.ofNullable(message.getDestinationPost()).map(Post::getId).orElse(null),
                Optional.ofNullable(message.getDestinationAnswerPost()).map(AnswerPost::getId).orElse(null));
    }
}
