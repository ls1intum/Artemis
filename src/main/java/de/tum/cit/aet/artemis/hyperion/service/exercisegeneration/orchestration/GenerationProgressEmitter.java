package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.util.function.BiPredicate;
import java.util.function.Consumer;

import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO;

/**
 * Records each event into the authoritative replayable transcript before pushing it to the live client, so an event the transcript rejects — anything after a terminal event —
 * is never published. A run emits roughly one line per bounded agent turn, so every accepted line can be pushed without batching.
 */
class GenerationProgressEmitter {

    private final BiPredicate<ExerciseGenerationEventDTO, Boolean> recordEvent;

    private final Consumer<ExerciseGenerationEventDTO> send;

    GenerationProgressEmitter(BiPredicate<ExerciseGenerationEventDTO, Boolean> recordEvent, Consumer<ExerciseGenerationEventDTO> send) {
        this.recordEvent = recordEvent;
        this.send = send;
    }

    void progress(String message) {
        ExerciseGenerationEventDTO event = ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.PROGRESS, message);
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
