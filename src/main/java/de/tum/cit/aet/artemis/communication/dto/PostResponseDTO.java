package de.tum.cit.aet.artemis.communication.dto;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.account.dto.UserSummaryDTO;
import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.DisplayPriority;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.UserRole;
import de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismCaseRefDTO;

/**
 * Cycle-free projection of {@link Post} for REST and websocket responses.
 * <p>
 * Mirrors the wire shape produced today by serializing the {@code Post} entity with its
 * {@code @JsonIncludeProperties}/{@code @JsonIgnoreProperties} configuration, but replaces every
 * nested entity reference with a cycle-free DTO so Jackson cannot hit its cyclic-reference race
 * during integration test deserialization (see {@code JacksonDeserializerInitializationConfig}).
 *
 * <h4>Cycle breaks</h4>
 * <ul>
 * <li>{@code Post.reactions} → {@link ReactionResponseDTO} which drops the {@code post}/{@code answerPost} back-refs.</li>
 * <li>{@code Post.answers} → {@link AnswerPostResponseDTO} which drops the {@code post} back-ref.</li>
 * <li>{@code Post.conversation} → {@link ConversationRefDTO} which carries only id/type/name/courseId.</li>
 * <li>{@code Post.plagiarismCase} → {@link PlagiarismCaseRefDTO} which carries only id/exerciseId/studentId.</li>
 * <li>{@code Post.author} → {@link UserSummaryDTO} which carries id/name/imageUrl/bot — matching the pre-refactor wire shape from {@code @JsonIncludeProperties} on
 * {@code Posting.author}.</li>
 * </ul>
 *
 * @param id                   the post id
 * @param author               the author of the post
 * @param authorRole           transient author role computed by the service layer
 * @param creationDate         when the post was created
 * @param updatedDate          when the post was last edited, {@code null} if never edited
 * @param content              the post content
 * @param title                the post title, {@code null} for messages
 * @param visibleForStudents   whether the post is visible to students
 * @param displayPriority      the display priority (e.g. pinned, none)
 * @param conversation         the conversation the post belongs to
 * @param plagiarismCase       the plagiarism case the post belongs to, {@code null} for regular messages
 * @param resolved             whether the post is marked as resolved
 * @param isSaved              whether the requesting user has bookmarked the post; transient
 * @param hasForwardedMessages whether this post has forwarded messages attached
 * @param reactions            reactions on the post
 * @param answers              answers on the post, ordered by creation date ascending
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PostResponseDTO(Long id, @Nullable UserSummaryDTO author, @Nullable UserRole authorRole, @Nullable ZonedDateTime creationDate, @Nullable ZonedDateTime updatedDate,
        @Nullable String content, @Nullable String title, @Nullable Boolean visibleForStudents, @Nullable DisplayPriority displayPriority,
        @Nullable ConversationRefDTO conversation, @Nullable PlagiarismCaseRefDTO plagiarismCase, boolean resolved, @JsonProperty("isSaved") boolean isSaved,
        boolean hasForwardedMessages, Set<ReactionResponseDTO> reactions, List<AnswerPostResponseDTO> answers) {

    /**
     * Build a {@link PostResponseDTO} from a {@link Post} entity.
     *
     * @param post the entity to project; must not be {@code null}
     * @return the projected response
     */
    public static PostResponseDTO from(Post post) {
        Set<ReactionResponseDTO> reactions = post.getReactions() == null ? Set.of()
                : post.getReactions().stream().map(ReactionResponseDTO::from).collect(Collectors.toUnmodifiableSet());
        List<AnswerPostResponseDTO> answers = post.getAnswers() == null ? List.of()
                : post.getAnswers().stream()
                        // Sort by creation date with a stable id tie-breaker so two answers with identical
                        // timestamps come back in the same order on every request — otherwise the source
                        // HashSet's iteration order would surface flaky payload ordering in clients.
                        .sorted(Comparator.comparing((AnswerPost a) -> a.getCreationDate(), Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(AnswerPost::getId,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                        .map(AnswerPostResponseDTO::from).toList();
        return new PostResponseDTO(post.getId(), UserSummaryDTO.from(post.getAuthor()), post.getAuthorRole(), post.getCreationDate(), post.getUpdatedDate(), post.getContent(),
                post.getTitle(), post.isVisibleForStudents(), post.getDisplayPriority(), ConversationRefDTO.from(post.getConversation()),
                PlagiarismCaseRefDTO.from(post.getPlagiarismCase()), post.isResolved(), post.getIsSaved(), post.getHasForwardedMessages(), reactions, answers);
    }
}
