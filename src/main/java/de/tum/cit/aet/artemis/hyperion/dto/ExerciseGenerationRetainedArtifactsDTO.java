package de.tum.cit.aet.artemis.hyperion.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The candidate a terminal generation run produced but never saved, retained read-only so the work stays inspectable instead of dying with the sandbox.
 * <p>
 * This is explicitly <em>not</em> a save. Nothing here was written to the exercise, no repository was committed to, no exercise version was recorded, and no code path can promote
 * this snapshot into one — a candidate that did not pass mechanical verification is structurally unpersistable. It exists so an instructor whose run spent half an hour and then
 * failed can read what was built and decide for themselves, and so a failed run stops being a black box.
 * <p>
 * Retention is bounded and expires with the rest of the run's replay evidence.
 *
 * @param jobId            the run that produced this candidate
 * @param completeness     whether these files are everything the run produced, or only what fit inside the retention bounds
 * @param problemStatement the student-facing statement as the agent left it; {@code null} when the run never wrote one
 * @param specDocument     the agent's {@code SPEC.md} planning document; {@code null} when the run never froze one
 * @param files            the produced repository files, ordered by repository and then path
 */
@Schema(description = "A generated candidate that was not saved to the exercise, retained read-only for inspection")
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
