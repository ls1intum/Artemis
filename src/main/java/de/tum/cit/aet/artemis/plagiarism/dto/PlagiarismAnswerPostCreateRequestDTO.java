package de.tum.cit.aet.artemis.plagiarism.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request payload for creating an answer post on a plagiarism case.
 * <p>
 * Replaces {@code @RequestBody AnswerPost} on {@code PlagiarismAnswerPostResource.createAnswerPost}.
 * The controller resolves the parent post from {@code postId} and constructs the answer entity
 * from these fields. {@code resolvesPost} is accepted on the wire for symmetry with
 * {@link PlagiarismAnswerPostUpdateRequestDTO} but is currently ignored on create — the server
 * always persists new answers with {@code resolvesPost=false} per
 * {@code PlagiarismAnswerPostService.createAnswerPost}.
 *
 * @param postId       id of the parent post this answer belongs to
 * @param content      the answer content
 * @param resolvesPost reserved; ignored on create
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismAnswerPostCreateRequestDTO(Long postId, @Nullable String content, boolean resolvesPost) {
}
