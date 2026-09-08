package de.tum.cit.aet.artemis.hyperion.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The bounded, read-only candidate snapshot of a running or terminal generation run.
 * <p>
 * This is <em>not</em> a save: exposing the snapshot writes neither repositories nor exercise metadata. The normal verification and persistence pipeline remains the only path from
 * the sandbox into the exercise. Retention is bounded and expires with the rest of the run's replay evidence.
 *
 * @param jobId            the run that produced this candidate
 * @param completeness     whether these files are everything the run produced, or only what fit inside the retention bounds
 * @param problemStatement the student-facing statement as the agent left it; {@code null} when the run never wrote one
 * @param specDocument     the agent's {@code SPEC.md} planning document; {@code null} when the run never froze one
 * @param files            the produced repository files, ordered by repository and then path
 */
@Schema(description = "The current bounded, read-only generated candidate, also retained for inspection when an unsaved run ends")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseGenerationRetainedArtifactsDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String jobId,
        @Schema(description = "Whether the retained files are a complete account of what the run produced", requiredMode = Schema.RequiredMode.REQUIRED) ExerciseGenerationArtifactCompleteness completeness,
        @Nullable String problemStatement, @Nullable String specDocument,
        @JsonInclude @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ExerciseGenerationRetainedFileDTO> files) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public ExerciseGenerationRetainedArtifactsDTO {
        files = List.copyOf(files);
    }

    /** Whether this snapshot carries anything worth returning; a snapshot with no files and no text is indistinguishable from having retained nothing. */
    public boolean isEmpty() {
        return files.isEmpty() && (problemStatement == null || problemStatement.isBlank()) && (specDocument == null || specDocument.isBlank());
    }
}
