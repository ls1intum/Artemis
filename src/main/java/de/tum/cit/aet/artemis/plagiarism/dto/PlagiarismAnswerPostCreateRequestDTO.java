package de.tum.cit.aet.artemis.plagiarism.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.dto.ParentPostDTO;

/**
 * Request payload for creating an answer post on a plagiarism case.
 * <p>
 * Replaces {@code @RequestBody AnswerPost} on {@code PlagiarismAnswerPostResource.createAnswerPost}.
 * Mirrors the wire shape of {@link de.tum.cit.aet.artemis.communication.dto.CreateAnswerPostDTO}
 * (used by the communication answer-post endpoint) so the web client's
 * {@code AnswerPostService.create()} can send the same {@code { content, post: { id } }} payload
 * to both endpoints. The controller resolves the parent post from {@code post.id} (rejected with
 * HTTP 400 if missing) and constructs the answer entity from these fields. {@code resolvesPost}
 * is accepted on the wire for symmetry with {@link PlagiarismAnswerPostUpdateRequestDTO} but is
 * currently ignored on create — the server always persists new answers with
 * {@code resolvesPost=false} per {@code PlagiarismAnswerPostService.createAnswerPost}.
 *
 * @param post         the parent post reference (id only); must be non-null
 * @param content      the answer content
 * @param resolvesPost reserved; ignored on create
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismAnswerPostCreateRequestDTO(@NotNull @Valid ParentPostDTO post, @Nullable String content, boolean resolvesPost) {
}
