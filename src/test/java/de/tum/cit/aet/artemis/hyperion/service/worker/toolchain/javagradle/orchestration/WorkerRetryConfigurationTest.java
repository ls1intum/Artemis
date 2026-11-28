package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.aiworker.api.InteractiveSandbox;
import de.tum.cit.aet.artemis.aiworker.api.SandboxApi;
import de.tum.cit.aet.artemis.core.service.llm.LlmClientService;
import de.tum.cit.aet.artemis.hyperion.protocol.ExecutionIdentity;
import de.tum.cit.aet.artemis.hyperion.protocol.ExerciseBrief;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationAssignment;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationOutput.AccountingState;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationParameters;
import de.tum.cit.aet.artemis.hyperion.protocol.GradingContext;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkspaceSnapshot;
import de.tum.cit.aet.artemis.hyperion.service.worker.GenerationObserver;
import io.micrometer.observation.ObservationRegistry;

class WorkerRetryConfigurationTest {

    /**
     * The preset's retries override the endpoint's. The client honours the effective value, and exercise generation refuses any preset that retries, because a
     * retried request is billed without a response the worker can account for.
     */
    @ParameterizedTest
    @CsvSource({ "0, , 1", "2, 0, 1", "0, 2, 2", "2, , 2" })
    void providerRequestsAndTheWorkerUseTheEffectiveRetriesOfThePreset(int endpointRetries, Integer presetRetries, int firstRequests) throws Exception {
        var requests = new AtomicInteger();
        SandboxApi sandbox = mock(SandboxApi.class);
        when(sandbox.forExecution(any())).thenReturn(mock(InteractiveSandbox.class));
        var provider = WorkerLlmTestContext.startProvider(exchange -> {
            boolean first = requests.incrementAndGet() == 1;
            WorkerLlmTestContext.reply(exchange, first ? 500 : 200, first ? "{\"error\":{\"message\":\"test failure\"}}" : WorkerLlmTestContext.SUCCESS);
        });
        try {
            var runner = WorkerLlmTestContext.runner().withBean(SandboxApi.class, () -> sandbox).withBean(JsonMapper.class, JsonMapper::new)
                    .withBean(ObservationRegistry.class, () -> ObservationRegistry.NOOP).withPropertyValues(WorkerLlmTestContext.baseUrlProperty(provider),
                            "artemis.llm.endpoints.test.timeout=2s", "artemis.llm.endpoints.test.max-retries=" + endpointRetries);
            if (presetRetries != null) {
                runner = runner.withPropertyValues("artemis.llm.presets.testpreset.max-retries=" + presetRetries);
            }
            runner.run(context -> {
                assertThat(context).hasNotFailed();
                var model = context.getBean(LlmClientService.class).chatModel("testpreset");
                if (firstRequests == 1) {
                    assertThatThrownBy(() -> model.call(new Prompt("test"))).isInstanceOfSatisfying(RestClientResponseException.class,
                            error -> assertThat(error.getStatusCode().value()).isEqualTo(500));
                    assertThat(requests).hasValue(1);
                    assertThat(model.call(new Prompt("test")).getResult().getOutput().getText()).isEqualTo("OK");
                }
                else {
                    assertThat(model.call(new Prompt("test")).getResult().getOutput().getText()).isEqualTo("OK");
                }
                assertThat(requests).hasValue(2);

                // Generate through the real adapter, stopping before any sandbox or model work.
                var adapter = context.getBean(JavaGradleGenerationAdapterService.class);
                if (firstRequests == 1) {
                    var result = adapter.generate(assignment(), () -> true, mock(GenerationObserver.class), ignored -> {
                    });
                    assertThat(result.accountingState()).isEqualTo(AccountingState.COMPLETE);
                    assertThat(result.usage().modelCalls()).isZero();
                }
                else {
                    assertThatIllegalStateException().isThrownBy(() -> adapter.generate(assignment(), () -> true, mock(GenerationObserver.class), ignored -> {
                    })).withMessageContaining("max-retries 0");
                }
                assertThat(requests).hasValue(2);
            });
        }
        finally {
            provider.stop(0);
        }
    }

    private static GenerationAssignment assignment() {
        var identity = new ExecutionIdentity("job", 1, UUID.randomUUID(), "worker", UUID.randomUUID());
        var brief = new ExerciseBrief("Stack", "stack", "de.example", null, "Create a stack", ExerciseBrief.Mode.GENERATE);
        var parameters = new GenerationParameters("standard", 10, 100_000, Duration.ofMinutes(5), 128_000, "testpreset", true, "CONTINUOUS", 0.5);
        return new GenerationAssignment(identity, brief, parameters, new WorkspaceSnapshot(List.of()), Instant.MAX, "sha256:" + "a".repeat(64),
                new GradingContext(false, Set.of(), Set.of()));
    }
}
