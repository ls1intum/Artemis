package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

import com.openai.core.http.Headers;
import com.openai.errors.BadRequestException;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.hyperion.protocol.GenerationOutput.AccountingState;
import de.tum.cit.aet.artemis.hyperion.protocol.ProviderUsageUpdate;
import de.tum.cit.aet.artemis.hyperion.protocol.ProviderUsageUpdate.Kind;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.ProviderFailureCooldown;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.ProviderUsageTransport;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.PromptTemplates;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.WorkerUsageRecorder;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.SpecFidelityCritic;

class WorkerAdvisoryAccountingStopTest {

    private static final String SPEC = "## Rules\n| R1 | choose the globally cheapest eligible request |\n";

    private static final String SOURCE = "package example; public class Scheduler { public int choose() { return 1; } }";

    private enum Failure {
        MISSING_USAGE, INDETERMINATE, DELIVERY
    }

    @ParameterizedTest
    @CsvSource({ "MISSING_USAGE, true", "MISSING_USAGE, false", "INDETERMINATE, true", "INDETERMINATE, false", "DELIVERY, true", "DELIVERY, false" })
    void fatalAccountingFailurePropagatesAndStopsWitness(Failure failure, boolean retriesDisabled) {
        ChatModel model = model();
        var firstCall = when(model.call(any(Prompt.class)));
        switch (failure) {
            case MISSING_USAGE -> firstCall.thenReturn(
                    new ChatResponse(List.of(new Generation(new AssistantMessage("{\"mutants\":[]}"))), ChatResponseMetadata.builder().usage(new EmptyUsage()).build()),
                    accounted("{\"witnesses\":[]}"));
            case INDETERMINATE -> firstCall.thenThrow(new IllegalStateException("indeterminate transport failure")).thenReturn(accounted("{\"witnesses\":[]}"));
            case DELIVERY -> firstCall.thenReturn(accounted("{\"mutants\":[]}"), accounted("{\"witnesses\":[]}"));
        }
        var events = new ArrayList<ProviderUsageUpdate>();
        var recorder = new WorkerUsageRecorder(1_000, 1, retriesDisabled, event -> {
            events.add(event);
            if (failure == Failure.DELIVERY && event.kind() == Kind.RESPONSE) {
                throw new IllegalStateException("usage delivery failed");
            }
        });
        var stop = JavaGradleGenerationAdapterService.stopAuthoring(() -> false, recorder, Instant.MAX);
        var critic = critic(model);
        assertThat(stop.getAsBoolean()).isFalse();

        var thrown = assertThatThrownBy(() -> critic.authorSemanticMutants(SPEC, Map.of("src/example/Scheduler.java", SOURCE), List.of(), recorder, stop));
        switch (failure) {
            case MISSING_USAGE -> thrown.isInstanceOf(IllegalArgumentException.class).hasMessage("Provider response contains no usage metadata");
            case INDETERMINATE -> thrown.isInstanceOf(IllegalStateException.class).hasMessage("indeterminate transport failure");
            case DELIVERY -> thrown.isInstanceOf(IllegalStateException.class).hasMessage("usage delivery failed");
        }

        assertThat(stop.getAsBoolean()).isTrue();
        assertThat(recorder.budgetExhausted()).isFalse();
        assertThat(critic.authorContractWitnesses(SPEC, "class SchedulerTest {}", SOURCE, recorder, stop)).isEmpty();
        verify(model, times(1)).call(any(Prompt.class));
        assertThat(recorder.accountingState()).isEqualTo(AccountingState.INCOMPLETE);
        assertThat(recorder.snapshot().modelCalls()).isEqualTo(failure == Failure.DELIVERY ? 1 : 0);
        assertThat(events).anyMatch(event -> event.kind() == Kind.UNCERTAIN || event.kind() == Kind.RESPONSE);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void knownRejectedMutantRequestPropagatesWithoutAccountingStop(boolean retriesDisabled) {
        ChatModel model = model();
        when(model.call(any(Prompt.class))).thenThrow(BadRequestException.builder().headers(Headers.builder().build()).build()).thenReturn(accounted("{\"witnesses\":[]}"));
        var recorder = new WorkerUsageRecorder(1_000, 1, retriesDisabled, ignored -> {
        });
        var stop = JavaGradleGenerationAdapterService.stopAuthoring(() -> false, recorder, Instant.MAX);
        var critic = critic(model);

        assertThatThrownBy(() -> critic.authorSemanticMutants(SPEC, Map.of("src/example/Scheduler.java", SOURCE), List.of(), recorder, stop))
                .isInstanceOf(BadRequestException.class);
        assertThat(stop.getAsBoolean()).isFalse();
        assertThat(critic.authorContractWitnesses(SPEC, "class SchedulerTest {}", SOURCE, recorder, stop)).isEmpty();

        verify(model, times(2)).call(any(Prompt.class));
        assertThat(recorder.snapshot().modelCalls()).isEqualTo(1);
        assertThat(recorder.accountingState()).isEqualTo(retriesDisabled ? AccountingState.COMPLETE : AccountingState.INCOMPLETE);
        assertThat(stop.getAsBoolean()).isFalse();
    }

    @Test
    void retryEnabledValidMutantAndWitnessResponsesRemainAllowed() {
        ChatModel model = model();
        when(model.call(any(Prompt.class))).thenReturn(accounted("{\"mutants\":[]}"), accounted("{\"witnesses\":[]}"));
        var recorder = new WorkerUsageRecorder(1_000, 1, false, ignored -> {
        });
        var stop = JavaGradleGenerationAdapterService.stopAuthoring(() -> false, recorder, Instant.MAX);
        var critic = critic(model);

        assertThat(critic.authorSemanticMutants(SPEC, Map.of("src/example/Scheduler.java", SOURCE), List.of(), recorder, stop)).isEmpty();
        assertThat(critic.authorContractWitnesses(SPEC, "class SchedulerTest {}", SOURCE, recorder, stop)).isEmpty();

        verify(model, times(2)).call(any(Prompt.class));
        assertThat(recorder.snapshot().modelCalls()).isEqualTo(2);
        assertThat(recorder.accountingState()).isEqualTo(AccountingState.INCOMPLETE);
        assertThat(stop.getAsBoolean()).isFalse();
    }

    @Test
    void adapterGuardStillHonorsCancellationAndDeadline() {
        var recorder = new WorkerUsageRecorder(1_000, 1, true, ignored -> {
        });

        assertThat(JavaGradleGenerationAdapterService.stopAuthoring(() -> false, recorder, Instant.MAX).getAsBoolean()).isFalse();
        assertThat(JavaGradleGenerationAdapterService.stopAuthoring(() -> true, recorder, Instant.MAX).getAsBoolean()).isTrue();
        assertThat(JavaGradleGenerationAdapterService.stopAuthoring(() -> false, recorder, Instant.EPOCH).getAsBoolean()).isTrue();
        assertThat(recorder.shouldStopAuthoring()).isFalse();
    }

    private static ChatModel model() {
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(OpenAiChatOptions.builder().model("reviewer").build());
        return model;
    }

    private static SpecFidelityCritic critic(ChatModel model) {
        return new SpecFidelityCritic(ChatClient.create(model), JsonMapper.builder().build(), new PromptTemplates(), "reviewer", Duration.ZERO, ProviderFailureCooldown.disabled(),
                128_000, List.of(model));
    }

    private static ChatResponse accounted(String text) {
        var metadata = ProviderUsageTransport.restore(new ProviderUsageUpdate.Call("reviewer", "request", 10, 2, 0L)).getMetadata();
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))), metadata);
    }
}
