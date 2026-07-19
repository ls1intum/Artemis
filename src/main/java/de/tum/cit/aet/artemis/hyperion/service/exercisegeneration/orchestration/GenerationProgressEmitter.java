package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.util.function.BiPredicate;
import java.util.function.Consumer;

import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO;

/**
 * Records each event into the authoritative replayable transcript before pushing it to the live client. Events rejected after a terminal transcript are not published. A
 * generation run is bounded (~15–90 turns, roughly one progress line per agent turn), so pushing each accepted line is not a flood.
 */
class GenerationProgressEmitter {

    private final BiPredicate<ExerciseGenerationEventDTO, Boolean> recordEvent;

    private final Consumer<ExerciseGenerationEventDTO> send;

    GenerationProgressEmitter(BiPredicate<ExerciseGenerationEventDTO, Boolean> recordEvent, Consumer<ExerciseGenerationEventDTO> send) {
        this.recordEvent = recordEvent;
        this.send = send;
    }

    /** Records a progress line and pushes it only when the transcript accepts it. */
    void progress(String message) {
        ExerciseGenerationEventDTO event = ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.PROGRESS, message);
        if (recordEvent.test(event, false)) {
            send.accept(event);
        }
    }

    /** Records a milestone and sends it only when accepted. Terminal milestones mark the transcript done. */
    void milestone(ExerciseGenerationEventDTO event) {
        boolean terminal = event.type() == ExerciseGenerationEventDTO.Type.DONE || event.type() == ExerciseGenerationEventDTO.Type.CANCELLED
                || event.type() == ExerciseGenerationEventDTO.Type.ERROR;
        if (recordEvent.test(event, terminal)) {
            send.accept(event);
        }
    }
}
