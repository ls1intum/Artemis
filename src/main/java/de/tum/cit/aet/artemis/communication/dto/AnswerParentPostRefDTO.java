package de.tum.cit.aet.artemis.communication.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.Post;

/**
 * Minimal back-reference from an {@link AnswerPostResponseDTO} to its parent {@link Post}.
 * <p>
 * Carries only the parent post id and a cycle-free {@link ConversationRefDTO} so the client can
 * route an answer to the correct conversation (the {@code AnswerPostService.getResourceEndpoint}
 * path inspects {@code answerPost.post.conversation} to pick between the communication and the
 * plagiarism answer-post endpoints) without re-introducing the JSON cycle that the broader DTO
 * migration was written to close. The parent {@link Post}'s {@code reactions} and {@code answers}
 * collections are intentionally not exposed here — including them would walk the same chain
 * ({@code Post.answers → AnswerPost.post → Post.reactions → Reaction.user → User["id"]}) that
 * fires Jackson's cyclic-reference race in {@code DeserializerCache._createAndCache2}.
 *
 * @param id           the parent post id
 * @param conversation the cycle-free projection of the parent post's conversation; {@code null}
 *                         when the parent post is not attached to a conversation (e.g. plagiarism posts)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AnswerParentPostRefDTO(Long id, @Nullable ConversationRefDTO conversation) {

    /**
     * Build an {@link AnswerParentPostRefDTO} from a {@link Post} entity. Returns {@code null}
     * when the input is {@code null} so callers can chain through nullable references without an
     * explicit guard.
     *
     * @param post the parent post to project, may be {@code null}
     * @return the projected reference, or {@code null} when {@code post} is {@code null}
     */
    public static @Nullable AnswerParentPostRefDTO from(@Nullable Post post) {
        if (post == null) {
            return null;
        }
        return new AnswerParentPostRefDTO(post.getId(), ConversationRefDTO.from(post.getConversation()));
    }
}
