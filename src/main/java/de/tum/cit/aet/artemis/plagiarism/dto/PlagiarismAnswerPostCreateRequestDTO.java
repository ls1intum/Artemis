package de.tum.cit.aet.artemis.plagiarism.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;

/**
 * Request payload for creating an answer post on a plagiarism case.
 * <p>
 * Replaces {@code @RequestBody AnswerPost} on {@code PlagiarismAnswerPostResource.createAnswerPost}.
 * The controller resolves the parent post and the {@link PlagiarismCase} from {@code postId} /
 * {@code plagiarismCaseId} and constructs the answer entity from these fields.
 *
 * @param postId           id of the parent post this answer belongs to
 * @param plagiarismCaseId id of the plagiarism case the parent post belongs to; nullable when the
 *                             server can infer it from the parent post
 * @param content          the answer content
 * @param resolvesPost     whether the answer resolves the parent post
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismAnswerPostCreateRequestDTO(Long postId, @Nullable Long plagiarismCaseId, @Nullable String content, boolean resolvesPost) {
}
