package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The reconnection view of an exercise-generation run, returned when a client (re)loads the page so it can replay the transcript and decide whether to keep listening.
 *
 * @param jobId           the job id (the websocket topic suffix)
 * @param running         whether the run is still active; owners subscribe for private updates, while other instructors poll the sanitized status
 * @param mode            the explicit run intent (generate vs. adapt), so a reconnecting client can restore the correct header label and the revert affordance without inferring it
 * @param events          the events produced so far, oldest first, to replay into the transcript
 * @param fileChanges     the latest lightweight change per file, in write order, for reconnect replay
 * @param revertAvailable whether the server still retains the baseline required to undo the latest saved generation or adaptation
 * @param revertJobId     the successful run whose baseline can be reverted; may differ from {@code jobId} when a later run failed or was cancelled
 * @param revertMode      the mode of {@code revertJobId}, used for truthful undo copy
 * @param ownedByCaller   whether the requesting instructor owns the active run and may inspect its retained details
 * @param cancellable     whether the active run is still in its disposable sandbox phase and can be cancelled safely
 * @param designDocument  the workspace's staged-generation {@code DESIGN.md} content, captured once the generation outcome landed, so the owner can review stage-0 design
 *                            quality; {@code null}/omitted for non-owner or sanitized views, or when the run never produced one
 * @param specDocument    the gate-approved {@code SPEC.md} behavioural specification, captured as soon as the spec gate passes (the earliest reviewable intermediate result);
 *                            {@code null}/omitted for non-owner or sanitized views, when the stage was skipped (an instructor statement served as the spec), or before the gate
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseGenerationStatusDTO(String jobId, boolean running, @Nullable GenerationMode mode, @JsonInclude List<ExerciseGenerationEventDTO> events,
        @JsonInclude List<ExerciseGenerationFileChangeDTO> fileChanges, boolean revertAvailable, @Nullable String revertJobId, @Nullable GenerationMode revertMode,
        boolean ownedByCaller, boolean cancellable, @Nullable String designDocument, @Nullable String specDocument) {

    public ExerciseGenerationStatusDTO(String jobId, boolean running, @Nullable GenerationMode mode, List<ExerciseGenerationEventDTO> events,
            List<ExerciseGenerationFileChangeDTO> fileChanges, boolean revertAvailable, @Nullable String revertJobId, @Nullable GenerationMode revertMode, boolean ownedByCaller,
            boolean cancellable) {
        this(jobId, running, mode, events, fileChanges, revertAvailable, revertJobId, revertMode, ownedByCaller, cancellable, null, null);
    }

    public ExerciseGenerationStatusDTO(String jobId, boolean running, @Nullable GenerationMode mode, List<ExerciseGenerationEventDTO> events,
            List<ExerciseGenerationFileChangeDTO> fileChanges, boolean revertAvailable, @Nullable String revertJobId, @Nullable GenerationMode revertMode, boolean ownedByCaller) {
        this(jobId, running, mode, events, fileChanges, revertAvailable, revertJobId, revertMode, ownedByCaller, running && ownedByCaller, null, null);
    }

    public ExerciseGenerationStatusDTO(String jobId, boolean running, @Nullable GenerationMode mode, List<ExerciseGenerationEventDTO> events,
            List<ExerciseGenerationFileChangeDTO> fileChanges, boolean revertAvailable, @Nullable String revertJobId, @Nullable GenerationMode revertMode) {
        this(jobId, running, mode, events, fileChanges, revertAvailable, revertJobId, revertMode, true, running, null, null);
    }

    public ExerciseGenerationStatusDTO(String jobId, boolean running, @Nullable GenerationMode mode, List<ExerciseGenerationEventDTO> events,
            List<ExerciseGenerationFileChangeDTO> fileChanges, boolean revertAvailable) {
        this(jobId, running, mode, events, fileChanges, revertAvailable, null, null, true, running, null, null);
    }
}
