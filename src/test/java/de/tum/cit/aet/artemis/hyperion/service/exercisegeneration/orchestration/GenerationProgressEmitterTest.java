package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationRepairRoundDTO;

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

        assertThat(sent).allSatisfy(push -> assertThat(push.type()).isEqualTo(ExerciseGenerationEventDTO.Type.PROGRESS));
        assertThat(sent.stream().map(ExerciseGenerationEventDTO::message).toList()).containsExactly("line 0", "line 1");
    }

    @Test
    void phaseProgress_isStructuredAndReplayable() {
        GenerationProgressEmitter emitter = newEmitter();

        emitter.phase(ExerciseGenerationEventDTO.Phase.VERIFYING, "Building both exercise variants");

        assertThat(sent).singleElement().satisfies(event -> {
            assertThat(event.phase()).isEqualTo(ExerciseGenerationEventDTO.Phase.VERIFYING);
            assertThat(event.message()).isEqualTo("Building both exercise variants");
        });
        assertThat(recorded).singleElement().satisfies(entry -> assertThat(entry.event().phase()).isEqualTo(ExerciseGenerationEventDTO.Phase.VERIFYING));
    }

    @Test
    void progressThenMilestone_areSentInOrder() {
        GenerationProgressEmitter emitter = newEmitter();

        emitter.progress("progress a");
        emitter.milestone(ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.STARTED, "milestone"));

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

        assertThat(recorded).allSatisfy(r -> {
            assertThat(r.event().type()).isEqualTo(ExerciseGenerationEventDTO.Type.PROGRESS);
            assertThat(r.terminal()).isFalse();
        });
        assertThat(recorded.stream().map(r -> r.event().message()).toList()).containsExactly("one", "two", "three");
    }

    // Every terminal type must be recorded with terminal=true, or a reconnecting client reads a finished run as still running.
    @ParameterizedTest
    @EnumSource(value = ExerciseGenerationEventDTO.Type.class, names = { "DONE", "CANCELLED", "ERROR" })
    void terminalEvent_isRecordedWithTerminalTrue(ExerciseGenerationEventDTO.Type type) {
        GenerationProgressEmitter emitter = newEmitter();

        emitter.milestone(ExerciseGenerationEventDTO.of(type, "finished"));

        assertThat(recorded).hasSize(1);
        assertThat(recorded.getFirst().event().type()).isEqualTo(type);
        assertThat(recorded.getFirst().terminal()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = ExerciseGenerationEventDTO.Type.class, names = { "STARTED", "PROGRESS" })
    void nonTerminalMilestone_isRecordedWithTerminalFalse(ExerciseGenerationEventDTO.Type type) {
        GenerationProgressEmitter emitter = newEmitter();

        emitter.milestone(ExerciseGenerationEventDTO.of(type, "ongoing"));

        assertThat(recorded).hasSize(1);
        assertThat(recorded.getFirst().terminal()).isFalse();
    }

    @Test
    void repairRoundLine_carriesItsCountsOnTheSameEventItStreams() {
        // The counts ride the event the transcript already carries; a parallel channel would be invisible to reconnect replay.
        GenerationProgressEmitter emitter = newEmitter();

        emitter.progress("Quality review round 2: 3 issues", new ExerciseGenerationRepairRoundDTO(2, 4, 2, 1, 2, 1, 1));

        assertThat(sent).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo(ExerciseGenerationEventDTO.Type.PROGRESS);
            assertThat(event.message()).isEqualTo("Quality review round 2: 3 issues");
            assertThat(event.repairRound()).isEqualTo(new ExerciseGenerationRepairRoundDTO(2, 4, 2, 1, 2, 1, 1));
        });
        assertThat(recorded).singleElement().satisfies(entry -> {
            assertThat(entry.terminal()).isFalse();
            assertThat(entry.event().repairRound().carriedOver()).isEqualTo(2);
        });
    }

    @Test
    void plainProgressLine_carriesNoRepairRound() {
        GenerationProgressEmitter emitter = newEmitter();

        emitter.progress("Setting up the build environment");

        assertThat(sent).singleElement().satisfies(event -> assertThat(event.repairRound()).isNull());
    }

    /** Every stage below the attempt loop takes a plain {@code Consumer<String>}, so a caller that supplies only a lambda must still receive the human-readable line. */
    @Test
    void aPlainConsumerSink_stillReceivesTheRoundLineWithoutTheCounts() {
        List<String> lines = new ArrayList<>();
        GenerationProgressSink sink = lines::add;

        sink.progress("Quality review round 1: 2 issues found.", new ExerciseGenerationRepairRoundDTO(1, 1, 2, 0, 0, 0, 2));

        assertThat(lines).containsExactly("Quality review round 1: 2 issues found.");
    }

    @Test
    void rejectedEvent_isNotSentToTheLiveClient() {
        GenerationProgressEmitter emitter = new GenerationProgressEmitter((event, terminal) -> false, sent::add);

        emitter.milestone(ExerciseGenerationEventDTO.done("late success", ExerciseGenerationEventDTO.CompletionStatus.SUCCESS, null, true));

        assertThat(sent).isEmpty();
    }
}
