package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO;

/**
 * Plain unit test (no Spring/websocket/Hazelcast) for {@link GenerationProgressEmitter}: it streams every progress line to the live client immediately (per-turn feedback, no
 * buffering lag), sends progress before a following milestone (the ordering invariant), and records every event to the transcript with the correct terminal flag.
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
    void eachProgressLine_isPushedImmediatelyAndVerbatim() {
        GenerationProgressEmitter emitter = newEmitter();

        emitter.progress("line 0");
        emitter.progress("line 1");

        // Each line is pushed to the live client immediately and verbatim (no coalescing), so the user sees per-turn progress without lag.
        assertThat(sent).hasSize(2);
        assertThat(sent).allSatisfy(push -> assertThat(push.type()).isEqualTo(ExerciseGenerationEventDTO.Type.PROGRESS));
        assertThat(sent.stream().map(ExerciseGenerationEventDTO::message).toList()).containsExactly("line 0", "line 1");
    }

    @Test
    void progressThenMilestone_areSentInOrder() {
        GenerationProgressEmitter emitter = newEmitter();

        emitter.progress("progress a");
        emitter.milestone(ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.STARTED, "milestone"));

        // The progress line is streamed first, then the milestone — order matters for a faithful live stream.
        assertThat(sent).hasSize(2);
        ExerciseGenerationEventDTO progress = sent.get(0);
        ExerciseGenerationEventDTO milestone = sent.get(1);
        assertThat(progress.type()).isEqualTo(ExerciseGenerationEventDTO.Type.PROGRESS);
        assertThat(progress.message()).isEqualTo("progress a");
        assertThat(milestone.type()).isEqualTo(ExerciseGenerationEventDTO.Type.STARTED);
        assertThat(milestone.message()).isEqualTo("milestone");
    }

    @Test
    void everyProgressLine_isRecordedToTranscriptIndividually() {
        GenerationProgressEmitter emitter = newEmitter();

        emitter.progress("one");
        emitter.progress("two");
        emitter.progress("three");

        // The transcript records each line individually and non-terminally (the source of truth on reconnect).
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
