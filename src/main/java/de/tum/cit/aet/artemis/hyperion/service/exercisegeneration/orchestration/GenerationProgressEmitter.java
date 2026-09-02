package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationActivityDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO.Phase;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationLiveUsageDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationRepairRoundDTO;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.GenerationActivityTracker;

/**
 * Records each event into the authoritative replayable transcript before pushing it to the live client, so an event the transcript rejects — anything after a terminal event —
 * is never published. A run emits roughly one line per bounded agent turn, so every accepted line can be pushed without batching.
 * <p>
 * One emitter is created per job, which is also the scope of the {@link GenerationActivityTracker} it owns: nothing in a run's activity accounting can leak into the next run.
 * <p>
 * The run's spend is stamped onto the events that already say what the run is doing — the activity lines and the phase boundaries — rather than onto every line: only the newest
 * snapshot is worth anything, and a plain progress line is exactly what the bounded transcript drops first.
 */
class GenerationProgressEmitter implements GenerationProgressSink {

    private final BiPredicate<ExerciseGenerationEventDTO, Boolean> recordEvent;

    private final Consumer<ExerciseGenerationEventDTO> send;

    private final Supplier<@Nullable ExerciseGenerationLiveUsageDTO> liveUsage;

    private final GenerationActivityTracker activityTracker = new GenerationActivityTracker();

    GenerationProgressEmitter(BiPredicate<ExerciseGenerationEventDTO, Boolean> recordEvent, Consumer<ExerciseGenerationEventDTO> send,
            Supplier<@Nullable ExerciseGenerationLiveUsageDTO> liveUsage) {
        this.recordEvent = recordEvent;
        this.send = send;
        this.liveUsage = liveUsage;
    }

    @Override
    public GenerationActivityTracker activityTracker() {
        return activityTracker;
    }

    @Override
    public void activity(String message, ExerciseGenerationActivityDTO activity) {
        emit(ExerciseGenerationEventDTO.activity(message, activity).withLiveUsage(liveUsage.get()));
    }

    void progress(String message) {
        emit(ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.PROGRESS, message));
    }

    @Override
    public void accept(String message) {
        progress(message);
    }

    @Override
    public void progress(String message, ExerciseGenerationRepairRoundDTO repairRound) {
        emit(ExerciseGenerationEventDTO.repairRound(message, repairRound));
    }

    @Override
    public void phase(Phase phase, String message) {
        emit(ExerciseGenerationEventDTO.phase(phase, message).withLiveUsage(liveUsage.get()));
    }

    private void emit(ExerciseGenerationEventDTO event) {
        if (recordEvent.test(event, false)) {
            send.accept(event);
        }
    }

    /** Terminal milestones mark the transcript done, after which it accepts nothing further. */
    void milestone(ExerciseGenerationEventDTO event) {
        boolean terminal = event.type() == ExerciseGenerationEventDTO.Type.DONE || event.type() == ExerciseGenerationEventDTO.Type.CANCELLED
                || event.type() == ExerciseGenerationEventDTO.Type.ERROR;
        if (recordEvent.test(event, terminal)) {
            send.accept(event);
        }
    }
}
