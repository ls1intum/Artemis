package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO;

/**
 * Records every event into the replayable transcript (the source of truth on reconnect) but coalesces the live websocket pushes for chatty progress lines so a long run does not
 * flood the client. Milestone and terminal events flush any buffered progress first, preserving order, then send immediately. Only ever used from the single generation thread, so
 * it needs no synchronisation.
 */
class GenerationProgressEmitter {

    /** Send the live websocket push after this many buffered progress lines; the transcript still records each line individually. */
    static final int FLUSH_EVERY = 4;

    private final BiConsumer<ExerciseGenerationEventDTO, Boolean> recordEvent;

    private final Consumer<ExerciseGenerationEventDTO> send;

    private final List<String> buffer = new ArrayList<>();

    /**
     * @param recordEvent records an event to the replayable transcript (event, terminal); invoked once per progress line and once per milestone
     * @param send        pushes an event to the live client; invoked at most once per coalesced flush and once per milestone
     */
    GenerationProgressEmitter(BiConsumer<ExerciseGenerationEventDTO, Boolean> recordEvent, Consumer<ExerciseGenerationEventDTO> send) {
        this.recordEvent = recordEvent;
        this.send = send;
    }

    /**
     * Records a progress line individually to the transcript and buffers it for the live client, sending a single coalesced push once the buffer reaches {@link #FLUSH_EVERY}.
     *
     * @param message the progress message
     */
    void progress(String message) {
        recordEvent.accept(ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.PROGRESS, message), false);
        buffer.add(message);
        if (buffer.size() >= FLUSH_EVERY) {
            flush();
        }
    }

    /**
     * Flushes any buffered progress first (preserving order), then records and sends the milestone immediately. Terminal milestones (DONE, CANCELLED, ERROR) are recorded with the
     * terminal flag so a reconnecting client knows the run finished.
     *
     * @param event the milestone (or terminal) event
     */
    void milestone(ExerciseGenerationEventDTO event) {
        flush();
        boolean terminal = event.type() == ExerciseGenerationEventDTO.Type.DONE || event.type() == ExerciseGenerationEventDTO.Type.CANCELLED
                || event.type() == ExerciseGenerationEventDTO.Type.ERROR;
        recordEvent.accept(event, terminal);
        send.accept(event);
    }

    private void flush() {
        if (buffer.isEmpty()) {
            return;
        }
        send.accept(ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.PROGRESS, String.join("\n", buffer)));
        buffer.clear();
    }
}
