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
 * The back-reference to the parent {@code Post} is intentionally omitted: an answer is always
 * returned embedded inside its parent {@code PostResponseDTO}, and dropping the reference is what
 * breaks the {@code Post ↔ AnswerPost} JSON cycle that triggers Jackson's cyclic-reference race
 * during integration test deserialization.
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
 * @param reactions            reactions on the answer
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AnswerPostResponseDTO(Long id, @Nullable UserSummaryDTO author, @Nullable UserRole authorRole, @Nullable ZonedDateTime creationDate,
        @Nullable ZonedDateTime updatedDate, @Nullable String content, boolean resolvesPost, @JsonProperty("isSaved") boolean isSaved, boolean hasForwardedMessages,
        Set<ReactionResponseDTO> reactions) {

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
                answerPost.getHasForwardedMessages(), reactions);
    }
}
