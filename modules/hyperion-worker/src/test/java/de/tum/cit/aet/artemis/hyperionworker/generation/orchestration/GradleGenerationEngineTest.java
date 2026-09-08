package de.tum.cit.aet.artemis.hyperionworker.generation.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import de.tum.cit.aet.artemis.hyperion.protocol.ExecutionIdentity;
import de.tum.cit.aet.artemis.hyperion.protocol.ExerciseBrief;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationAssignment;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationOutput.AccountingState;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationParameters;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationProgress;
import de.tum.cit.aet.artemis.hyperion.protocol.GradingContext;
import de.tum.cit.aet.artemis.hyperion.protocol.ProviderUsageUpdate;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkspaceSnapshot;
import de.tum.cit.aet.artemis.hyperionworker.sandbox.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperionworker.session.GenerationObserver;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;

class GradleGenerationEngineTest {

    private final InteractiveSandbox sandbox = mock(InteractiveSandbox.class);

    private final ChatModel model = mock(ChatModel.class);

    private final GenerationObserver observer = mock(GenerationObserver.class);

    private GradleGenerationEngine engine(int retries) {
        when(model.getOptions()).thenReturn(OpenAiChatOptions.builder().model("test-model").build());
        return new GradleGenerationEngine(sandbox, List.of(model), ObservationRegistry.NOOP, 6, retries, "");
    }

    private static GenerationAssignment assignment(Instant deadline) {
        var identity = new ExecutionIdentity("job", 1, UUID.randomUUID(), "worker", UUID.randomUUID());
        var brief = new ExerciseBrief("Stack", "stack", "de.example", null, "Create a stack", ExerciseBrief.Mode.GENERATE);
        var parameters = new GenerationParameters("standard", 10, 100_000, Duration.ofMinutes(5), 128_000, null, null, null, null, null, true, "CONTINUOUS");
        return new GenerationAssignment(identity, brief, parameters, new WorkspaceSnapshot(List.of()), deadline, "sha256:" + "a".repeat(64), new GradingContext(false, Set.of()));
    }

    @Test
    void preCancelledAssignmentDoesNotCreateASandboxOrPublishACheckpoint() {
        var engine = engine(0);
        AtomicInteger checkpoints = new AtomicInteger();

        var result = engine.generate(assignment(Instant.now().plusSeconds(300)), () -> true, observer, ignored -> checkpoints.incrementAndGet());

        assertThat(result.terminationReason()).isEqualTo("CANCELLED");
        assertThat(result.candidate().files()).isEmpty();
        assertThat(result.verifiedDigest()).isNull();
        assertThat(result.accountingState()).isEqualTo(AccountingState.COMPLETE);
        assertThat(checkpoints).hasValue(0);
        assertThat(engine.requestCancel()).isFalse();
        verifyNoInteractions(sandbox, observer);
    }

    @Test
    void expiredAdmissionDeadlineStopsBeforeSandboxCreation() {
        var engine = engine(1);

        var result = engine.generate(assignment(Instant.now().minusSeconds(1)), () -> false, observer, ignored -> {
            throw new AssertionError("No candidate can be verified after an already expired admission");
        });

        assertThat(result.terminationReason()).isEqualTo("CANCELLED");
        assertThat(result.verifiedDigest()).isNull();
        assertThat(result.accountingState()).isEqualTo(AccountingState.INCOMPLETE);
        assertThat(engine.requestCancel()).isFalse();
        verifyNoInteractions(sandbox);
        verify(observer).progress("Provider usage recorded.", new GenerationProgress(null, null, null, new ProviderUsageUpdate(ProviderUsageUpdate.Kind.UNCERTAIN, 0, null)));
    }

    @Test
    void generationObservationCorrelatesTheAssignmentAndClosesItsScope() {
        var registry = ObservationRegistry.create();
        var stopped = new ArrayList<Observation.Context>();
        registry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {

            @Override
            public boolean supportsContext(Observation.Context context) {
                return true;
            }

            @Override
            public void onStop(Observation.Context context) {
                stopped.add(context);
            }
        });
        when(model.getOptions()).thenReturn(OpenAiChatOptions.builder().model("test-model").build());
        var engine = new GradleGenerationEngine(sandbox, List.of(model), registry, 6, 0, "");
        var assignment = assignment(Instant.now().plusSeconds(300));
        engine.generate(assignment, () -> {
            assertThat(registry.getCurrentObservation()).isNotNull();
            return true;
        }, observer, ignored -> {
        });

        assertThat(registry.getCurrentObservation()).isNull();
        assertThat(stopped).singleElement().satisfies(context -> {
            assertThat(context.getName()).isEqualTo("hyperion.generation");
            assertThat(context.getHighCardinalityKeyValue("artemis.hyperion.job.id").getValue()).isEqualTo(assignment.identity().jobId());
            assertThat(context.getHighCardinalityKeyValue("artemis.hyperion.execution.id").getValue()).isEqualTo(assignment.identity().executionId().toString());
            assertThat(context.getHighCardinalityKeyValue("artemis.exercise.id").getValue()).isEqualTo("1");
            assertThat(context.getLowCardinalityKeyValue("artemis.hyperion.effort_profile").getValue()).isEqualTo("standard");
        });
    }

    @Test
    void invalidOperatorConfigurationFailsAtStartup() {
        assertThatIllegalArgumentException().isThrownBy(() -> new GradleGenerationEngine(sandbox, List.of(), ObservationRegistry.NOOP, 6, 0, ""));
        assertThatIllegalArgumentException().isThrownBy(() -> new GradleGenerationEngine(sandbox, List.of(model, model), ObservationRegistry.NOOP, 6, 0, ""));
        assertThatIllegalArgumentException().isThrownBy(() -> new GradleGenerationEngine(sandbox, List.of(model), ObservationRegistry.NOOP, 0, 0, ""));
        assertThatIllegalArgumentException().isThrownBy(() -> new GradleGenerationEngine(sandbox, List.of(model), ObservationRegistry.NOOP, 13, 0, ""));
        assertThatIllegalArgumentException().isThrownBy(() -> new GradleGenerationEngine(sandbox, List.of(model), ObservationRegistry.NOOP, 6, -1, ""));
    }
}
