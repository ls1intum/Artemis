package de.tum.cit.aet.artemis.plagiarism.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request payload for updating an existing answer post on a plagiarism case.
 * <p>
 * Replaces {@code @RequestBody AnswerPost} on {@code PlagiarismAnswerPostResource.updateAnswerPost}.
 * Only the fields an update is allowed to mutate are present; the controller resolves the existing
 * answer post by its path-variable id and applies these fields.
 *
 * @param content      the new answer content
 * @param resolvesPost whether the answer resolves its parent post
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismAnswerPostUpdateRequestDTO(@Nullable String content, boolean resolvesPost) {
}
