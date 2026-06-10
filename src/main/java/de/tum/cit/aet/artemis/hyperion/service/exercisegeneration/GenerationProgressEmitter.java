package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO;

/**
 * Records every event into the replayable transcript (the source of truth on reconnect) AND pushes it to the live client immediately, so the user sees per-turn progress with no
 * buffering lag. A generation run is bounded (~15–90 turns, roughly one progress line per agent turn), so pushing each line is not a flood. Only ever used from the single
 * generation thread, so it needs no synchronisation.
 */
class GenerationProgressEmitter {

    private final BiConsumer<ExerciseGenerationEventDTO, Boolean> recordEvent;

    private final Consumer<ExerciseGenerationEventDTO> send;

    GenerationProgressEmitter(BiConsumer<ExerciseGenerationEventDTO, Boolean> recordEvent, Consumer<ExerciseGenerationEventDTO> send) {
        this.recordEvent = recordEvent;
        this.send = send;
    }

    /** Records a progress line to the transcript and pushes it to the live client. */
    void progress(String message) {
        ExerciseGenerationEventDTO event = ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.PROGRESS, message);
        recordEvent.accept(event, false);
        send.accept(event);
    }

    /** Records and sends a milestone. Terminal milestones (DONE, CANCELLED, ERROR) are recorded with the terminal flag so a reconnecting client knows the run finished. */
    void milestone(ExerciseGenerationEventDTO event) {
        boolean terminal = event.type() == ExerciseGenerationEventDTO.Type.DONE || event.type() == ExerciseGenerationEventDTO.Type.CANCELLED
                || event.type() == ExerciseGenerationEventDTO.Type.ERROR;
        recordEvent.accept(event, terminal);
        send.accept(event);
    }
}
