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

    private SandboxAgentTools delegate;

    private List<ExerciseGenerationFileChangeDTO> emitted;

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
        tools.onTurn(4);

        tools.writeFile("/workspace/solution/src/A.java", "first");
        tools.writeFile("solution/src/A.java", "second");

        assertThat(emitted).hasSize(2);
        assertThat(emitted.getFirst()).satisfies(change -> {
            assertThat(change.type()).isEqualTo(ExerciseGenerationFileChangeDTO.TYPE);
            assertThat(change.path()).isEqualTo("solution/src/A.java");
            assertThat(change.repo()).isEqualTo(ExerciseGenerationFileChangeDTO.REPOSITORY_SOLUTION);
            assertThat(change.action()).isEqualTo(ExerciseGenerationFileChangeDTO.ACTION_WRITE);
            assertThat(change.turn()).isEqualTo(4);
        });
        assertThat(emitted.getLast().action()).isEqualTo(ExerciseGenerationFileChangeDTO.ACTION_WRITE);
    }

    @Test
    void successfulEditEmitsWithoutReadingFileContentBack() {
        when(delegate.editFile("tests/T.java", "old", "new")).thenReturn(WRITE_OK);

        assertThat(tools.editFile("tests/T.java", "old", "new")).isEqualTo(WRITE_OK);

        assertThat(emitted).singleElement().satisfies(change -> {
            assertThat(change.repo()).isEqualTo(ExerciseGenerationFileChangeDTO.REPOSITORY_TESTS);
            assertThat(change.action()).isEqualTo(ExerciseGenerationFileChangeDTO.ACTION_EDIT);
        });
    }

    @Test
    void successfulDeleteEmitsDeleteAndRecreationIsWrite() {
        when(delegate.deleteFile("solution/A.java")).thenReturn("Deleted solution/A.java");
        when(delegate.writeFile(anyString(), anyString())).thenReturn(WRITE_OK);
        tools.writeFile("solution/A.java", "old");

        tools.deleteFile("solution/A.java");
        tools.writeFile("solution/A.java", "new");

        assertThat(emitted).extracting(ExerciseGenerationFileChangeDTO::action).containsExactly("write", "delete", "write");
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
    void readBashVerifyAndSubmitRemainPureDelegations() {
        when(delegate.readFile("solution/A.java")).thenReturn("content");
        when(delegate.bash("ls")).thenReturn("out");
        when(delegate.verify()).thenReturn("verified");
        when(delegate.submit("done")).thenReturn("submitted");

        assertThat(tools.readFile("solution/A.java")).isEqualTo("content");
        assertThat(tools.bash("ls")).isEqualTo("out");
        assertThat(tools.verify()).isEqualTo("verified");
        assertThat(tools.submit("done")).isEqualTo("submitted");
        assertThat(emitted).isEmpty();
    }
}
