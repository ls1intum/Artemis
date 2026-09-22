package de.tum.cit.aet.artemis.hyperion.service.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.hyperion.protocol.ExecutionIdentity;
import de.tum.cit.aet.artemis.hyperion.protocol.ExerciseBrief;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationAssignment;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationOutput;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationParameters;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationToolchain;
import de.tum.cit.aet.artemis.hyperion.protocol.GradingContext;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkspaceSnapshot;

class DefaultGenerationEngineTest {

    private static final String IMAGE = "sha256:" + "a".repeat(64);

    private static final GenerationToolchain PYTHON = new GenerationToolchain("python-pytest");

    @Test
    void dispatchesNonJavaAssignmentWithoutInterpretingItsNamespaceAndDelegatesCancellation() {
        var adapter = adapter(PYTHON);
        var engine = new DefaultGenerationEngineService(PYTHON.id(), List.of(adapter));
        var assignment = assignment(PYTHON);
        BooleanSupplier cancelled = () -> false;
        var progress = mock(GenerationObserver.class);
        Consumer<GenerationOutput> checkpoint = output -> {
        };
        var expected = mock(GenerationOutput.class);
        when(adapter.generate(assignment, cancelled, progress, checkpoint)).thenReturn(expected);
        when(adapter.requestCancel(assignment.identity())).thenReturn(true);

        assertThat(engine.generate(assignment, cancelled, progress, checkpoint)).isSameAs(expected);
        assertThat(engine.requestCancel(assignment.identity())).isTrue();
        verify(adapter).generate(assignment, cancelled, progress, checkpoint);
        verify(adapter).requestCancel(assignment.identity());
    }

    @Test
    void refusesMissingOrAmbiguousAdapterAtStartup() {
        assertThatIllegalArgumentException().isThrownBy(() -> new DefaultGenerationEngineService(PYTHON.id(), List.of(adapter(GenerationToolchain.JAVA_GRADLE))));
        assertThatIllegalArgumentException().isThrownBy(() -> new DefaultGenerationEngineService(PYTHON.id(), List.of(adapter(PYTHON), adapter(PYTHON))));
    }

    @Test
    void mismatchedAssignmentCannotReachAuthoring() {
        var adapter = adapter(GenerationToolchain.JAVA_GRADLE);
        var engine = new DefaultGenerationEngineService(GenerationToolchain.JAVA_GRADLE.id(), List.of(adapter));
        var progress = mock(GenerationObserver.class);
        assertThatIllegalArgumentException().isThrownBy(() -> engine.generate(assignment(PYTHON), () -> false, progress, output -> {
        }));
        verifyNoInteractions(progress);
        org.mockito.Mockito.verify(adapter, org.mockito.Mockito.never()).generate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private static ToolchainGenerationAdapter adapter(GenerationToolchain toolchain) {
        var adapter = mock(ToolchainGenerationAdapter.class);
        when(adapter.toolchain()).thenReturn(toolchain);
        return adapter;
    }

    private static GenerationAssignment assignment(GenerationToolchain toolchain) {
        var identity = new ExecutionIdentity("job", 1, UUID.randomUUID(), "worker", UUID.randomUUID());
        var brief = new ExerciseBrief("Title", "short", "my-package", null, "Create", ExerciseBrief.Mode.GENERATE, null, toolchain);
        var parameters = new GenerationParameters("standard", 10, 100_000, Duration.ofMinutes(5), 128_000, null, null, null, null, null, true, "CONTINUOUS");
        return new GenerationAssignment(identity, brief, parameters, new WorkspaceSnapshot(List.of()), Instant.now().plusSeconds(300), IMAGE, new GradingContext(false, Set.of()));
    }
}
