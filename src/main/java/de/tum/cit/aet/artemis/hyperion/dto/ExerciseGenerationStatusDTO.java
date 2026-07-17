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
 * @param fileSnapshots   the latest whole-file snapshot per file written so far, in write order, so a reloading client can rehydrate the live editor preview and resume the stream
 * @param revertAvailable whether the server still retains the baseline required to undo the latest saved generation or adaptation
 * @param revertJobId     the successful run whose baseline can be reverted; may differ from {@code jobId} when a later run failed or was cancelled
 * @param revertMode      the mode of {@code revertJobId}, used for truthful undo copy
 * @param ownedByCaller   whether the requesting instructor owns the active run and may inspect its retained details
 * @param cancellable     whether the active run is still in its disposable sandbox phase and can be cancelled safely
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseGenerationStatusDTO(String jobId, boolean running, @Nullable GenerationMode mode, @JsonInclude List<ExerciseGenerationEventDTO> events,
        @JsonInclude List<ExerciseGenerationFileSnapshotDTO> fileSnapshots, boolean revertAvailable, @Nullable String revertJobId, @Nullable GenerationMode revertMode,
        boolean ownedByCaller, boolean cancellable) {

    public ExerciseGenerationStatusDTO(String jobId, boolean running, @Nullable GenerationMode mode, List<ExerciseGenerationEventDTO> events,
            List<ExerciseGenerationFileSnapshotDTO> fileSnapshots, boolean revertAvailable, @Nullable String revertJobId, @Nullable GenerationMode revertMode,
            boolean ownedByCaller) {
        this(jobId, running, mode, events, fileSnapshots, revertAvailable, revertJobId, revertMode, ownedByCaller, running && ownedByCaller);
    }

    public ExerciseGenerationStatusDTO(String jobId, boolean running, @Nullable GenerationMode mode, List<ExerciseGenerationEventDTO> events,
            List<ExerciseGenerationFileSnapshotDTO> fileSnapshots, boolean revertAvailable, @Nullable String revertJobId, @Nullable GenerationMode revertMode) {
        this(jobId, running, mode, events, fileSnapshots, revertAvailable, revertJobId, revertMode, true, running);
    }

    public ExerciseGenerationStatusDTO(String jobId, boolean running, @Nullable GenerationMode mode, List<ExerciseGenerationEventDTO> events,
            List<ExerciseGenerationFileSnapshotDTO> fileSnapshots, boolean revertAvailable) {
        this(jobId, running, mode, events, fileSnapshots, revertAvailable, null, null, true, running);
    }
}
