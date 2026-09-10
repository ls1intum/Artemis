package de.tum.cit.aet.artemis.communication.dto;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.ForwardedMessage;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.PostingType;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;

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

    /**
     * Converts this DTO to a ForwardedMessage entity.
     *
     * @return the ForwardedMessage entity
     */
    public ForwardedMessage toEntity() {
        // A forwarded message points at exactly one destination, and both ways of getting that wrong come from the
        // client. Two ids set makes the second setter below throw IllegalStateException, which surfaces as a 500.
        // Neither id set is not rejected anywhere, so it persists a row that the retrieval endpoint can never return,
        // because that groups messages by their destination. Both are reported here as the one malformed request.
        if ((this.destinationPostId == null) == (this.destinationAnswerPostId == null)) {
            throw new BadRequestAlertException("A forwarded message must have exactly one destination, either a destination post or a destination answer post", "forwardedMessage",
                    "forwardedMessageNeedsExactlyOneDestination");
        }
        ForwardedMessage message = new ForwardedMessage();
        message.setId(this.id);
        message.setSourceId(this.sourceId);
        message.setSourceType(this.sourceType);

        if (this.destinationPostId != null) {
            Post post = new Post();
            post.setId(this.destinationPostId);
            message.setDestinationPost(post);
        }

        if (this.destinationAnswerPostId != null) {
            AnswerPost answerPost = new AnswerPost();
            answerPost.setId(this.destinationAnswerPostId);
            message.setDestinationAnswerPost(answerPost);
        }

        return message;
    }
}
