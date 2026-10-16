package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for requesting code generation for a programming exercise.
 * Contains the repository type to determine which generation strategy to use.
 * Set initialAutoGeneration to true for the first automatically-triggered end-to-end generation flow.
 * The repository type is required; it is nullable here only so that a missing one is rejected with a proper error.
 * To ask whether a job is already running, use the active-job endpoint instead of this request.
 *
 * @param checkOnly only sent by clients loaded before the active-job endpoint existed, which asked for the running job with
 *                      {@code {"checkOnly": true}} and no repository type. Accepted so that an editor tab left open across a
 *                      deployment can still restore a running job instead of failing with a missing repository type.
 *                      TODO: Remove in the release after 10.1, once no client of 10.0 or earlier can still be open.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CodeGenerationRequestDTO(@Nullable RepositoryType repositoryType, boolean initialAutoGeneration, @Nullable List<Long> selectedFeedbackThreadIds,
        @Deprecated(forRemoval = true, since = "10.1") @Schema(hidden = true) boolean checkOnly) {

    public CodeGenerationRequestDTO(@Nullable RepositoryType repositoryType) {
        this(repositoryType, false, null);
    }

    public CodeGenerationRequestDTO(@Nullable RepositoryType repositoryType, boolean initialAutoGeneration) {
        this(repositoryType, initialAutoGeneration, null);
    }

    public CodeGenerationRequestDTO(@Nullable RepositoryType repositoryType, boolean initialAutoGeneration, @Nullable List<Long> selectedFeedbackThreadIds) {
        this(repositoryType, initialAutoGeneration, selectedFeedbackThreadIds, false);
    }
}
