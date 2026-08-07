package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The reconnection view of an exercise-generation run, returned when a client (re)loads the page so it can replay the transcript and decide whether to keep listening.
 *
 * @param jobId           the job id (the websocket topic suffix)
 * @param running         whether the run is still active
 * @param mode            the explicit run intent, so a reconnecting client does not have to infer it
 * @param events          the events produced so far, oldest first, to replay into the transcript
 * @param fileChanges     the latest lightweight change per file, in write order, for reconnect replay
 * @param revertAvailable whether the server still retains the baseline required to undo the latest saved generation or adaptation
 * @param revertJobId     the successful run whose baseline can be reverted; may differ from {@code jobId} when a later run failed or was cancelled
 * @param revertMode      the mode of {@code revertJobId}
 * @param ownedByCaller   whether the requesting instructor owns the active run and may inspect its retained details
 * @param cancellable     whether the active run is still in its disposable sandbox phase and can be cancelled safely
 * @param specDocument    the gate-approved {@code SPEC.md} behavioural specification; omitted for non-owner or sanitized views, when the stage was skipped, or before the gate
 * @param usage           aggregate model usage for this terminal run; owner-only and absent while running or when accounting could not be loaded
 * @param accountingState how complete {@code usage} is as an account of this run's provider spend. A running job is {@code PENDING}; terminal status without retained usage is
 *                            {@code INCOMPLETE}
 * @param effortProfile   the effort profile this run actually resolved to; omitted for sanitized views and for deployments that configure no profiles
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseGenerationStatusDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String jobId, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean running,
        @Nullable GenerationMode mode, @JsonInclude @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ExerciseGenerationEventDTO> events,
        @JsonInclude @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ExerciseGenerationFileChangeDTO> fileChanges,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean revertAvailable, @Nullable String revertJobId, @Nullable GenerationMode revertMode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean ownedByCaller, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean cancellable,
        @Nullable String specDocument, @Nullable ExerciseGenerationUsageDTO usage,
        @Schema(description = "Whether the reported usage is a complete account of a generation run's provider spend", requiredMode = Schema.RequiredMode.REQUIRED) ExerciseGenerationAccountingState accountingState,
        @Nullable String effortProfile) {

    public ExerciseGenerationStatusDTO(String jobId, boolean running, @Nullable GenerationMode mode, List<ExerciseGenerationEventDTO> events,
            List<ExerciseGenerationFileChangeDTO> fileChanges, boolean revertAvailable, @Nullable String revertJobId, @Nullable GenerationMode revertMode, boolean ownedByCaller,
            boolean cancellable, @Nullable String specDocument) {
        this(jobId, running, mode, events, fileChanges, revertAvailable, revertJobId, revertMode, ownedByCaller, cancellable, specDocument, null,
                running ? ExerciseGenerationAccountingState.PENDING : ExerciseGenerationAccountingState.INCOMPLETE, null);
    }

    public ExerciseGenerationStatusDTO(String jobId, boolean running, @Nullable GenerationMode mode, List<ExerciseGenerationEventDTO> events,
            List<ExerciseGenerationFileChangeDTO> fileChanges, boolean revertAvailable, @Nullable String revertJobId, @Nullable GenerationMode revertMode, boolean ownedByCaller,
            boolean cancellable) {
        this(jobId, running, mode, events, fileChanges, revertAvailable, revertJobId, revertMode, ownedByCaller, cancellable, null, null,
                running ? ExerciseGenerationAccountingState.PENDING : ExerciseGenerationAccountingState.INCOMPLETE, null);
    }

    public ExerciseGenerationStatusDTO(String jobId, boolean running, @Nullable GenerationMode mode, List<ExerciseGenerationEventDTO> events,
            List<ExerciseGenerationFileChangeDTO> fileChanges, boolean revertAvailable, @Nullable String revertJobId, @Nullable GenerationMode revertMode, boolean ownedByCaller) {
        this(jobId, running, mode, events, fileChanges, revertAvailable, revertJobId, revertMode, ownedByCaller, running && ownedByCaller, null, null,
                running ? ExerciseGenerationAccountingState.PENDING : ExerciseGenerationAccountingState.INCOMPLETE, null);
    }

    public ExerciseGenerationStatusDTO(String jobId, boolean running, @Nullable GenerationMode mode, List<ExerciseGenerationEventDTO> events,
            List<ExerciseGenerationFileChangeDTO> fileChanges, boolean revertAvailable, @Nullable String revertJobId, @Nullable GenerationMode revertMode) {
        this(jobId, running, mode, events, fileChanges, revertAvailable, revertJobId, revertMode, true, running, null, null,
                running ? ExerciseGenerationAccountingState.PENDING : ExerciseGenerationAccountingState.INCOMPLETE, null);
    }

    public ExerciseGenerationStatusDTO(String jobId, boolean running, @Nullable GenerationMode mode, List<ExerciseGenerationEventDTO> events,
            List<ExerciseGenerationFileChangeDTO> fileChanges, boolean revertAvailable) {
        this(jobId, running, mode, events, fileChanges, revertAvailable, null, null, true, running, null, null,
                running ? ExerciseGenerationAccountingState.PENDING : ExerciseGenerationAccountingState.INCOMPLETE, null);
    }

    public static ExerciseGenerationStatusDTO revertOnly(String jobId, GenerationMode mode) {
        return new ExerciseGenerationStatusDTO(jobId, false, mode, List.of(), List.of(), true, jobId, mode);
    }

    public ExerciseGenerationStatusDTO withUsage(@Nullable ExerciseGenerationUsageDTO usage, ExerciseGenerationAccountingState accountingState) {
        return new ExerciseGenerationStatusDTO(jobId, running, mode, events, fileChanges, revertAvailable, revertJobId, revertMode, ownedByCaller, cancellable, specDocument, usage,
                accountingState, effortProfile);
    }

    public ExerciseGenerationStatusDTO withEffortProfile(@Nullable String effortProfile) {
        return new ExerciseGenerationStatusDTO(jobId, running, mode, events, fileChanges, revertAvailable, revertJobId, revertMode, ownedByCaller, cancellable, specDocument, usage,
                accountingState, effortProfile);
    }
}
