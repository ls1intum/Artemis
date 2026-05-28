package de.tum.cit.aet.artemis.communication.dto;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.account.dto.UserSummaryDTO;
import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.UserRole;

/**
 * Cycle-free projection of {@link AnswerPost} for REST and websocket responses.
 * <p>
 * The {@link #post} field carries only a {@link AnswerParentPostRefDTO} — the parent post's id
 * plus a cycle-free {@link ConversationRefDTO}. That is enough for the web client to route the
 * answer to the correct conversation (its {@code AnswerPostService.getResourceEndpoint} reads
 * {@code answerPost.post.conversation} to disambiguate communication vs plagiarism answer-post
 * endpoints) while still cutting the {@code Post ↔ AnswerPost} JSON cycle that triggers Jackson's
 * cyclic-reference race during integration test deserialization. {@link AnswerParentPostRefDTO}
 * does not expose the parent's {@code reactions} or {@code answers} collections, so the cycle
 * {@code AnswerPost.post → Post.answers → AnswerPost.post → ...} cannot be reconstructed from the
 * wire payload.
 *
 * @param id                   the answer id
 * @param author               the author of the answer
 * @param authorRole           the transient author role computed by the service layer; nullable when not enriched
 * @param creationDate         when the answer was created
 * @param updatedDate          when the answer was last edited, {@code null} if never edited
 * @param content              the answer content
 * @param resolvesPost         whether the answer resolves the parent post
 * @param isSaved              whether the requesting user has bookmarked the answer; transient
 * @param hasForwardedMessages whether this answer has forwarded messages attached
 * @param post                 cycle-free parent-post reference (id + conversation) for client routing
 * @param reactions            reactions on the answer
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AnswerPostResponseDTO(Long id, @Nullable UserSummaryDTO author, @Nullable UserRole authorRole, @Nullable ZonedDateTime creationDate,
        @Nullable ZonedDateTime updatedDate, @Nullable String content, boolean resolvesPost, @JsonProperty("isSaved") boolean isSaved, boolean hasForwardedMessages,
        @Nullable AnswerParentPostRefDTO post, Set<ReactionResponseDTO> reactions) {

    /**
     * Build an {@link AnswerPostResponseDTO} from an {@link AnswerPost} entity.
     *
     * @param answerPost the entity to project; must not be {@code null}
     * @return the projected response
     */
    public static AnswerPostResponseDTO from(AnswerPost answerPost) {
        Set<ReactionResponseDTO> reactions = answerPost.getReactions() == null ? Set.of()
                : answerPost.getReactions().stream().map(ReactionResponseDTO::from).collect(Collectors.toUnmodifiableSet());
        return new AnswerPostResponseDTO(answerPost.getId(), UserSummaryDTO.from(answerPost.getAuthor()), answerPost.getAuthorRole(), answerPost.getCreationDate(),
                answerPost.getUpdatedDate(), answerPost.getContent(), Boolean.TRUE.equals(answerPost.doesResolvePost()), answerPost.getIsSaved(),
                answerPost.getHasForwardedMessages(), AnswerParentPostRefDTO.from(answerPost.getPost()), reactions);
    }
}
