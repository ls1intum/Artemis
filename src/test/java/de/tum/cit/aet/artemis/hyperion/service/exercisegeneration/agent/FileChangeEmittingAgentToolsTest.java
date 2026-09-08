package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationFileChangeDTO;

class FileChangeEmittingAgentToolsTest {

    private static final String WRITE_OK = "Wrote 3 characters to path";

    private static final String EDIT_OK = "Replaced 1 occurrence in tests/T.java.";

    private SandboxAgentTools delegate;

    private List<GenerationFileUpdate> emitted;

    private FileChangeEmittingAgentTools tools;

    @BeforeEach
    void setUp() {
        delegate = mock(SandboxAgentTools.class);
        emitted = new ArrayList<>();
        tools = new FileChangeEmittingAgentTools(delegate, emitted::add);
    }

    @Test
    void successfulWritesEmitLightweightWriteChanges() {
        when(delegate.writeFile(anyString(), anyString())).thenReturn(WRITE_OK);
        when(delegate.latestMutationContent()).thenReturn("first", "second");
        tools.onTurn(4);

        tools.writeFile("/workspace/solution/src/A.java", "first");
        tools.writeFile("solution/src/A.java", "second");

        assertThat(emitted).hasSize(2);
        assertThat(emitted.getFirst()).satisfies(update -> {
            var change = update.change();
            assertThat(change.type()).isEqualTo(ExerciseGenerationFileChangeDTO.TYPE);
            assertThat(change.path()).isEqualTo("solution/src/A.java");
            assertThat(change.repo()).isEqualTo(ExerciseGenerationFileChangeDTO.REPOSITORY_SOLUTION);
            assertThat(change.action()).isEqualTo(ExerciseGenerationFileChangeDTO.ACTION_WRITE);
            assertThat(change.turn()).isEqualTo(4);
        });
        assertThat(emitted.getLast().change().action()).isEqualTo(ExerciseGenerationFileChangeDTO.ACTION_WRITE);
        assertThat(emitted).extracting(GenerationFileUpdate::content).containsExactly("first", "second");
    }

    @Test
    void successfulEditEmitsWithoutReadingFileContentBack() {
        when(delegate.editFile("tests/T.java", "old", "new")).thenReturn(EDIT_OK);
        when(delegate.latestMutationContent()).thenReturn("complete edited file");

        assertThat(tools.editFile("tests/T.java", "old", "new")).isEqualTo(EDIT_OK);

        assertThat(emitted).singleElement().satisfies(update -> {
            var change = update.change();
            assertThat(change.repo()).isEqualTo(ExerciseGenerationFileChangeDTO.REPOSITORY_TESTS);
            assertThat(change.action()).isEqualTo(ExerciseGenerationFileChangeDTO.ACTION_EDIT);
            assertThat(update.content()).isEqualTo("complete edited file");
        });
    }

    @Test
    void successfulDeleteEmitsDeleteAndRecreationIsWrite() {
        when(delegate.deleteFile("solution/A.java")).thenReturn("Deleted solution/A.java");
        when(delegate.writeFile(anyString(), anyString())).thenReturn(WRITE_OK);
        tools.writeFile("solution/A.java", "old");

        tools.deleteFile("solution/A.java");
        tools.writeFile("solution/A.java", "new");

        assertThat(emitted).extracting(update -> update.change().action()).containsExactly("write", "delete", "write");
    }

    @Test
    void unsafePathsAndFailedWritesEmitNothing() {
        when(delegate.writeFile(anyString(), anyString())).thenReturn(WRITE_OK, "ERROR: disk full");

        tools.writeFile("../etc/passwd", "x");
        tools.writeFile("solution/A.java", "x");

        assertThat(emitted).isEmpty();
    }

    @Test
    void sinkFailureDoesNotFailTheAgentWrite() {
        FileChangeEmittingAgentTools throwingSink = new FileChangeEmittingAgentTools(delegate, change -> {
            throw new RuntimeException("sink exploded");
        });
        when(delegate.writeFile(anyString(), anyString())).thenReturn(WRITE_OK);

        assertThatCode(() -> assertThat(throwingSink.writeFile("solution/A.java", "x")).isEqualTo(WRITE_OK)).doesNotThrowAnyException();
    }

    @Test
    void readSearchBashVerifyAndSubmitRemainPureDelegations() {
        when(delegate.readFile("solution/A.java", null, null)).thenReturn("content");
        when(delegate.search("problem-statement.md", "empty")).thenReturn("3:empty");
        when(delegate.bash("ls")).thenReturn("out");
        when(delegate.verify()).thenReturn("verified");
        when(delegate.submit("done")).thenReturn("submitted");

        assertThat(tools.readFile("solution/A.java", null, null)).isEqualTo("content");
        assertThat(tools.search("problem-statement.md", "empty")).isEqualTo("3:empty");
        assertThat(tools.bash("ls")).isEqualTo("out");
        assertThat(tools.verify()).isEqualTo("verified");
        assertThat(tools.submit("done")).isEqualTo("submitted");
        assertThat(emitted).isEmpty();
    }

    @Test
    void consumeSubmitVetoDelegatesToTheWrappedTools() {
        when(delegate.consumeSubmitVeto()).thenReturn(true, false);

        assertThat(tools.consumeSubmitVeto()).isTrue();
        assertThat(tools.consumeSubmitVeto()).isFalse();
    }

    @Test
    void onlySuccessfulFileMutationsAreCountedIntoTheRunActivity() {
        // Counted here and nowhere else: this decorator is already the one place that knows a write, edit, or delete actually landed.
        GenerationActivityTracker tracker = new GenerationActivityTracker();
        FileChangeEmittingAgentTools countingTools = new FileChangeEmittingAgentTools(delegate, emitted::add, tracker);
        when(delegate.writeFile(anyString(), anyString())).thenReturn(WRITE_OK, "ERROR: outside the workspace");
        when(delegate.editFile(anyString(), anyString(), anyString())).thenReturn(EDIT_OK);
        when(delegate.deleteFile(anyString())).thenReturn("ERROR: no such file");

        countingTools.writeFile("solution/src/A.java", "first");
        countingTools.writeFile("../escape.java", "second");
        countingTools.editFile("tests/T.java", "old", "new");
        countingTools.deleteFile("tests/Missing.java");

        assertThat(tracker.snapshot().filesWritten()).isEqualTo(2);
    }

    @Test
    void withoutATracker_theDecoratorStillStreamsFileChanges() {
        when(delegate.writeFile(anyString(), anyString())).thenReturn(WRITE_OK);

        tools.writeFile("solution/src/A.java", "first");

        assertThat(emitted).hasSize(1);
    }
}
