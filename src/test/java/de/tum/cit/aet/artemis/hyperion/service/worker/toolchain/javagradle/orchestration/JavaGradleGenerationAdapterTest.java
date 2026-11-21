package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import de.tum.cit.aet.artemis.aiworker.api.InteractiveSandbox;
import de.tum.cit.aet.artemis.aiworker.api.SandboxApi;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties;
import de.tum.cit.aet.artemis.core.config.llm.LlmTestCatalog;
import de.tum.cit.aet.artemis.core.service.llm.LlmClientService;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.hyperion.protocol.ExecutionIdentity;
import de.tum.cit.aet.artemis.hyperion.protocol.ExerciseBrief;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationAssignment;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationOutput.AccountingState;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationParameters;
import de.tum.cit.aet.artemis.hyperion.protocol.GradingContext;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkspaceSnapshot;
import de.tum.cit.aet.artemis.hyperion.service.worker.GenerationObserver;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;

class JavaGradleGenerationAdapterTest {

    private final SandboxApi sandbox = mock(SandboxApi.class);

    private final InteractiveSandbox executionSandbox = mock(InteractiveSandbox.class);

    private final ChatModel model = mock(ChatModel.class);

    private final GenerationObserver observer = mock(GenerationObserver.class);

    private final LlmClientService llmClientService = mock(LlmClientService.class);

    private JavaGradleGenerationAdapterService engine(int retries) {
        when(sandbox.forExecution(org.mockito.ArgumentMatchers.any())).thenReturn(executionSandbox);
        presetWithRetries(retries);
        return adapter(ObservationRegistry.NOOP, 6);
    }

    private void presetWithRetries(int retries) {
        when(model.getOptions()).thenReturn(OpenAiChatOptions.builder().model("test-model").build());
        when(llmClientService.preset("testpreset")).thenReturn(LlmTestCatalog.preset("test", "test-model", LlmProperties.Api.RESPONSES));
        when(llmClientService.effectiveMaxRetries("testpreset")).thenReturn(retries);
        when(llmClientService.effectiveTimeout("testpreset")).thenReturn(Duration.ofMinutes(10));
        when(llmClientService.chatModel("testpreset")).thenReturn(model);
    }

    private JavaGradleGenerationAdapterService adapter(ObservationRegistry registry, int maxSemanticRepairs) {
        return new JavaGradleGenerationAdapterService(sandbox, llmClientService, JsonObjectMapper.get(), registry, maxSemanticRepairs, "");
    }

    private static GenerationAssignment assignment(Instant deadline) {
        var identity = new ExecutionIdentity("job", 1, UUID.randomUUID(), "worker", UUID.randomUUID());
        var brief = new ExerciseBrief("Stack", "stack", "de.example", null, "Create a stack", ExerciseBrief.Mode.GENERATE);
        var parameters = new GenerationParameters("standard", 10, 100_000, Duration.ofMinutes(5), 128_000, "testpreset", true, "CONTINUOUS", 0.5);
        return new GenerationAssignment(identity, brief, parameters, new WorkspaceSnapshot(List.of()), deadline, "sha256:" + "a".repeat(64),
                new GradingContext(false, Set.of(), Set.of()));
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "class", "de.class", "int.example", "de._", "de..example", "my-package" })
    void rejectsInvalidJavaNamespacesBeforeModelOrSandboxWork(String namespace) {
        var a = assignment(Instant.now().plusSeconds(300));
        var brief = new ExerciseBrief("Stack", "stack", namespace, null, "Create", ExerciseBrief.Mode.GENERATE);
        var invalid = new GenerationAssignment(a.identity(), brief, a.parameters(), a.seed(), a.authoringDeadline(), a.imageDigest(), a.gradingContext());
        var adapter = adapter(ObservationRegistry.NOOP, 6);
        assertThatIllegalArgumentException().isThrownBy(() -> adapter.generate(invalid, () -> false, observer, ignored -> {
        }));
        verifyNoInteractions(model, sandbox, observer, llmClientService);
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
        assertThat(engine.requestCancel(assignment(Instant.now()).identity())).isFalse();
        verifyNoInteractions(observer);
        verify(executionSandbox, org.mockito.Mockito.never()).createSession();
    }

    @Test
    void unknownPresetOrPresetWithRetriesFailsTheRunBeforeSandboxWork() {
        var engine = engine(1);
        assertThatIllegalStateException().isThrownBy(() -> engine.generate(assignment(Instant.now().plusSeconds(300)), () -> false, observer, ignored -> {
        })).withMessageContaining("max-retries 0");

        when(llmClientService.preset("testpreset")).thenThrow(new IllegalArgumentException("unknown"));
        assertThatIllegalStateException().isThrownBy(() -> engine.generate(assignment(Instant.now().plusSeconds(300)), () -> false, observer, ignored -> {
        })).withMessageContaining("no LLM preset 'testpreset'");
        verifyNoInteractions(executionSandbox, observer);
    }

    @Test
    void settingsUseThePresetTimeoutAndModelOptions() {
        var engine = engine(0);
        var settings = engine.settings(assignment(Instant.now()).parameters(), model);

        assertThat(settings.llmPreset()).isEqualTo("testpreset");
        assertThat(settings.chatOptions()).isInstanceOfSatisfying(OpenAiChatOptions.class, options -> {
            assertThat(options.getModel()).isEqualTo("test-model");
            assertThat(options.getTimeout()).isEqualTo(Duration.ofMinutes(10));
        });
    }

    @Test
    void expiredAdmissionDeadlineStopsBeforeSandboxCreation() {
        var engine = engine(0);

        var result = engine.generate(assignment(Instant.now().minusSeconds(1)), () -> false, observer, ignored -> {
            throw new AssertionError("No candidate can be verified after an already expired admission");
        });

        assertThat(result.terminationReason()).isEqualTo("CANCELLED");
        assertThat(result.verifiedDigest()).isNull();
        assertThat(result.accountingState()).isEqualTo(AccountingState.COMPLETE);
        assertThat(engine.requestCancel(assignment(Instant.now()).identity())).isFalse();
        verify(executionSandbox, org.mockito.Mockito.never()).createSession();
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
        presetWithRetries(0);
        when(sandbox.forExecution(org.mockito.ArgumentMatchers.any())).thenReturn(executionSandbox);
        var engine = adapter(registry, 6);
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
    void concurrentRunsUseSeparateSandboxesAndExactCancellationIdentities() throws Exception {
        var engine = engine(0);
        var first = assignment(Instant.now().plusSeconds(300));
        var second = assignment(Instant.now().plusSeconds(300));
        var firstSandbox = mock(InteractiveSandbox.class);
        var secondSandbox = mock(InteractiveSandbox.class);
        when(sandbox.forExecution(first.identity().executionId())).thenReturn(firstSandbox);
        when(sandbox.forExecution(second.identity().executionId())).thenReturn(secondSandbox);
        var entered = new java.util.concurrent.CountDownLatch(2);
        var releaseFirst = new java.util.concurrent.CountDownLatch(1);
        var releaseSecond = new java.util.concurrent.CountDownLatch(1);
        when(firstSandbox.createSession()).thenAnswer(call -> {
            entered.countDown();
            assertThat(releaseFirst.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            throw new IllegalStateException("Test ends before authoring");
        });
        when(secondSandbox.createSession()).thenAnswer(call -> {
            entered.countDown();
            assertThat(releaseSecond.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            throw new IllegalStateException("Test ends before authoring");
        });
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var firstRun = executor.submit(() -> engine.generate(first, () -> false, observer, ignored -> {
            }));
            var secondRun = executor.submit(() -> engine.generate(second, () -> false, observer, ignored -> {
            }));
            try {
                assertThat(entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                assertThat(engine.requestCancel(first.identity())).isTrue();
                releaseFirst.countDown();
                try {
                    firstRun.get(5, java.util.concurrent.TimeUnit.SECONDS);
                }
                catch (java.util.concurrent.ExecutionException expected) {
                    assertThat(expected.getCause()).isInstanceOf(IllegalStateException.class);
                }
                assertThat(engine.requestCancel(first.identity())).isFalse();
                assertThat(engine.requestCancel(second.identity())).isTrue();
                assertThat(secondRun.isDone()).isFalse();
            }
            finally {
                releaseFirst.countDown();
                releaseSecond.countDown();
            }
        }
    }

    @Test
    void invalidOperatorConfigurationFailsAtStartup() {
        assertThatIllegalArgumentException().isThrownBy(() -> adapter(ObservationRegistry.NOOP, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> adapter(ObservationRegistry.NOOP, 13));
    }
}
