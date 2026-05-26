package de.tum.cit.aet.artemis.plagiarism.dto;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request payload for creating an answer post on a plagiarism case.
 * <p>
 * Replaces {@code @RequestBody AnswerPost} on {@code PlagiarismAnswerPostResource.createAnswerPost}.
 * The controller resolves the parent post from {@code postId} (rejected with HTTP 400 if missing)
 * and constructs the answer entity from these fields. {@code resolvesPost} is accepted on the
 * wire for symmetry with {@link PlagiarismAnswerPostUpdateRequestDTO} but is currently ignored
 * on create — the server always persists new answers with {@code resolvesPost=false} per
 * {@code PlagiarismAnswerPostService.createAnswerPost}.
 *
 * @param postId       id of the parent post this answer belongs to; must be non-null
 * @param content      the answer content
 * @param resolvesPost reserved; ignored on create
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismAnswerPostCreateRequestDTO(@NotNull Long postId, @Nullable String content, boolean resolvesPost) {
}
