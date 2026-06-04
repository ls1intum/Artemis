package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO;

/**
 * Plain unit test (no Spring/websocket/Hazelcast) for {@link GenerationProgressEmitter}: it coalesces chatty progress pushes {@value GenerationProgressEmitter#FLUSH_EVERY}:1,
 * flushes the buffer BEFORE a milestone/terminal event (ordering invariant), and records every line to the transcript individually.
 */
class GenerationProgressEmitterTest {

    /** A recorded transcript entry: the event and whether it terminated the run. */
    private record Recorded(ExerciseGenerationEventDTO event, boolean terminal) {
    }

    private final List<Recorded> recorded = new ArrayList<>();

    private final List<ExerciseGenerationEventDTO> sent = new ArrayList<>();

    private GenerationProgressEmitter newEmitter() {
        return new GenerationProgressEmitter((event, terminal) -> recorded.add(new Recorded(event, terminal)), sent::add);
    }

    @Test
    void progressBelowThreshold_buffersWithoutSending() {
        GenerationProgressEmitter emitter = newEmitter();

        for (int i = 0; i < GenerationProgressEmitter.FLUSH_EVERY - 1; i++) {
            emitter.progress("line " + i);
        }

        // Below the flush threshold: nothing is pushed to the live client yet, but every line is already in the transcript.
        assertThat(sent).isEmpty();
        assertThat(recorded).hasSize(GenerationProgressEmitter.FLUSH_EVERY - 1);
    }

    @Test
    void thresholdLine_sendsOneCoalescedPush() {
        GenerationProgressEmitter emitter = newEmitter();

        for (int i = 0; i < GenerationProgressEmitter.FLUSH_EVERY; i++) {
            emitter.progress("line " + i);
        }

        // The threshold-th line triggers exactly one coalesced push containing all buffered lines joined by newlines.
        assertThat(sent).hasSize(1);
        ExerciseGenerationEventDTO push = sent.getFirst();
        assertThat(push.type()).isEqualTo(ExerciseGenerationEventDTO.Type.PROGRESS);
        assertThat(push.message()).isEqualTo("line 0\nline 1\nline 2\nline 3");
    }

    @Test
    void milestone_flushesBufferFirstThenSendsMilestone_inOrder() {
        GenerationProgressEmitter emitter = newEmitter();

        emitter.progress("buffered a");
        emitter.progress("buffered b");
        assertThat(sent).isEmpty();

        emitter.milestone(ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.STARTED, "milestone"));

        // The buffered progress flush must be sent FIRST, then the milestone — order matters for a faithful live stream.
        assertThat(sent).hasSize(2);
        ExerciseGenerationEventDTO flushed = sent.get(0);
        ExerciseGenerationEventDTO milestone = sent.get(1);
        assertThat(flushed.type()).isEqualTo(ExerciseGenerationEventDTO.Type.PROGRESS);
        assertThat(flushed.message()).isEqualTo("buffered a\nbuffered b");
        assertThat(milestone.type()).isEqualTo(ExerciseGenerationEventDTO.Type.STARTED);
        assertThat(milestone.message()).isEqualTo("milestone");
    }

    @Test
    void everyProgressLine_isRecordedToTranscriptIndividually() {
        GenerationProgressEmitter emitter = newEmitter();

        emitter.progress("one");
        emitter.progress("two");
        emitter.progress("three");

        // Coalescing only affects the live pushes; the transcript still records each line individually and non-terminally.
        assertThat(recorded).hasSize(3);
        assertThat(recorded).allSatisfy(r -> {
            assertThat(r.event().type()).isEqualTo(ExerciseGenerationEventDTO.Type.PROGRESS);
            assertThat(r.terminal()).isFalse();
        });
        assertThat(recorded.stream().map(r -> r.event().message()).toList()).containsExactly("one", "two", "three");
    }

    @Test
    void terminalEvent_isRecordedWithTerminalTrue() {
        GenerationProgressEmitter emitter = newEmitter();

        emitter.milestone(ExerciseGenerationEventDTO.done("finished", ExerciseGenerationEventDTO.CompletionStatus.SUCCESS, null));

        // A DONE event terminates the run, so the transcript record must carry terminal=true; a non-terminal milestone (e.g. STARTED) must not.
        assertThat(recorded).hasSize(1);
        assertThat(recorded.getFirst().event().type()).isEqualTo(ExerciseGenerationEventDTO.Type.DONE);
        assertThat(recorded.getFirst().terminal()).isTrue();
    }

    @Test
    void nonTerminalMilestone_isRecordedWithTerminalFalse() {
        GenerationProgressEmitter emitter = newEmitter();

        emitter.milestone(ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.STARTED, "started"));

        assertThat(recorded).hasSize(1);
        assertThat(recorded.getFirst().terminal()).isFalse();
    }
}
