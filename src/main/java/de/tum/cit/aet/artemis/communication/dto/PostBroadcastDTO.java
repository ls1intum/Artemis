package de.tum.cit.aet.artemis.communication.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.Post;

/**
 * Wire-format payload sent over STOMP for post-related events.
 * <p>
 * Carries a cycle-free {@link PostResponseDTO} so clients deserializing the frame never traverse
 * the {@code Post → reactions → Reaction → user → User} or {@code Post → answers → AnswerPost.post}
 * cycles that previously fired Jackson's {@code "No _valueDeserializer assigned"} race during
 * integration test deserialization.
 *
 * @param post   the cycle-free post projection
 * @param action which CRUD action this broadcast describes
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PostBroadcastDTO(PostResponseDTO post, MetisCrudAction action) {

    /**
     * Build a {@link PostBroadcastDTO} from a {@link Post} entity and a {@link MetisCrudAction}.
     *
     * @param post   the post entity to project
     * @param action which CRUD action this broadcast describes
     * @return the broadcast-shaped payload
     */
    public static PostBroadcastDTO from(Post post, MetisCrudAction action) {
        return new PostBroadcastDTO(PostResponseDTO.from(post), action);
    }
}
