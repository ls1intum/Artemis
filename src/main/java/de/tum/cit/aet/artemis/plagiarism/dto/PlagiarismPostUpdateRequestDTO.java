package de.tum.cit.aet.artemis.plagiarism.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request payload for updating an existing plagiarism post.
 * <p>
 * Replaces {@code @RequestBody Post} on {@code PlagiarismPostResource.updatePost}. Only the fields
 * a plagiarism-post update is allowed to mutate are present; the controller resolves the existing
 * post by its path-variable id and applies these fields.
 *
 * @param title   the new post title; nullable for content-only edits
 * @param content the new post content
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismPostUpdateRequestDTO(@Nullable String title, @Nullable String content) {
}
