package de.tum.cit.aet.artemis.metis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import com.openai.core.http.Headers;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIRetryableException;
import com.openai.errors.OpenAIServiceException;
import com.openai.errors.UnexpectedStatusCodeException;

import de.tum.cit.aet.artemis.admin.domain.LLMServiceType;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.core.util.TimeUtil;
import de.tum.cit.aet.artemis.metis.config.MetisConfiguration;
import de.tum.cit.aet.artemis.metis.config.MetisProperties;
import de.tum.cit.aet.artemis.metis.service.MetisLlmService.Mode;
import de.tum.cit.aet.artemis.metis.service.MetisLlmService.Pipeline;
import de.tum.cit.aet.artemis.metis.util.MetisTestProperties;

class MetisLlmServiceTest {

    private static final MetisLlmService.Attribution ATTRIBUTION = new MetisLlmService.Attribution(11L, 22L, 33L);

    private static final String CONFIGURED_MODEL = "configured-model";

    private static final ZonedDateTime NOW = ZonedDateTime.parse("2026-10-12T10:00:00Z");

    private final ChatModel batchModel = mock(ChatModel.class);

    private final ChatModel interactiveModel = mock(ChatModel.class);

    private final LLMTokenUsageService llmTokenUsageService = mock(LLMTokenUsageService.class);

    private final List<ExecutorService> executors = new ArrayList<>();

    private MetisLlmService llmService;

    @BeforeEach
    void setUp() {
        // Since Spring AI 2.0 the ChatClient merges request options into the model's options, which must be non-null
        when(batchModel.getOptions()).thenReturn(ChatOptions.builder().build());
        when(interactiveModel.getOptions()).thenReturn(ChatOptions.builder().build());
        llmService = llmService(MetisTestProperties.defaults());
    }

    @AfterEach
    void tearDown() {
        TimeUtil.resetClock();
        executors.forEach(ExecutorService::shutdownNow);
    }

    @Test
    void shouldReturnTheTextAndTheModelTheEndpointReported() {
        when(batchModel.call(any(Prompt.class))).thenReturn(response("{\"questions\":[]}", "qwen3-coder:30b"));

        MetisLlmService.Completion completion = llmService.complete(Pipeline.QUESTION_GENERATION, Mode.BATCH, "system", "user", ATTRIBUTION);

        assertThat(completion.text()).isEqualTo("{\"questions\":[]}");
        assertThat(completion.model()).isEqualTo("qwen3-coder:30b");
        verify(interactiveModel, never()).call(any(Prompt.class));
    }

    @Test
    void shouldSendThePromptsAsPlainText() {
        when(interactiveModel.call(any(Prompt.class))).thenReturn(response("rated", "model"));
        String code = "class A { int f() { return x; } } // {{notAPlaceholder}} {braces}";

        llmService.complete(Pipeline.ANSWER_RATING, Mode.INTERACTIVE, "Rate {this}", code, ATTRIBUTION);

        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(interactiveModel).call(prompt.capture());
        assertThat(prompt.getValue().getSystemMessage().getText()).isEqualTo("Rate {this}");
        assertThat(prompt.getValue().getUserMessage().getText()).isEqualTo(code);
        verify(batchModel, never()).call(any(Prompt.class));
    }

    @Test
    void shouldFallBackToTheConfiguredModelWhenTheEndpointReportsNone() {
        when(batchModel.call(any(Prompt.class))).thenReturn(response("text", ""));

        assertThat(llmService.complete(Pipeline.QUESTION_GENERATION, Mode.BATCH, "system", "user", ATTRIBUTION).model()).isEqualTo(CONFIGURED_MODEL);
    }

    @Test
    void shouldCutTheReportedModelToTheColumnWidth() {
        when(batchModel.call(any(Prompt.class))).thenReturn(response("text", "m".repeat(150)));

        assertThat(llmService.complete(Pipeline.QUESTION_GENERATION, Mode.BATCH, "system", "user", ATTRIBUTION).model()).hasSize(100);
    }

    @Test
    void shouldTrackTheTokensUnderMetisWithThePipelineAndTheAttribution() {
        ChatResponse response = response("rated", "model");
        when(interactiveModel.call(any(Prompt.class))).thenReturn(response);

        llmService.complete(Pipeline.ANSWER_RATING, Mode.INTERACTIVE, "system", "user", ATTRIBUTION);

        ArgumentCaptor<Function<LLMTokenUsageService.LLMTokenUsageBuilder, LLMTokenUsageService.LLMTokenUsageBuilder>> builder = ArgumentCaptor.captor();
        verify(llmTokenUsageService).trackChatResponseTokenUsage(eq(response), eq(LLMServiceType.METIS), eq("METIS_ANSWER_RATING"), builder.capture());
        LLMTokenUsageService.LLMTokenUsageBuilder trace = builder.getValue().apply(new LLMTokenUsageService.LLMTokenUsageBuilder());
        assertThat(trace.getCourseID()).contains(11L);
        assertThat(trace.getExerciseID()).contains(22L);
        assertThat(trace.getUserID()).contains(33L);
    }

    @Test
    void shouldUseTheIdOfEachPipeline() {
        assertThat(Pipeline.QUESTION_GENERATION.pipelineId()).isEqualTo("METIS_QUESTION_GENERATION");
        assertThat(Pipeline.QUESTION_REGENERATION.pipelineId()).isEqualTo("METIS_QUESTION_REGENERATION");
        assertThat(Pipeline.ANSWER_RATING.pipelineId()).isEqualTo("METIS_ANSWER_RATING");
    }

    @Test
    void shouldRejectBlankTextAsInvalidOutputAfterTrackingIt() {
        ChatResponse response = response("  \n ", "model");
        when(batchModel.call(any(Prompt.class))).thenReturn(response);

        assertKind(() -> llmService.complete(Pipeline.QUESTION_GENERATION, Mode.BATCH, "system", "user", ATTRIBUTION), MetisLlmException.Kind.INVALID_OUTPUT);
        verify(llmTokenUsageService).trackChatResponseTokenUsage(eq(response), eq(LLMServiceType.METIS), eq("METIS_QUESTION_GENERATION"), any());
    }

    @Test
    void shouldRejectAResponseWithoutGenerationAsInvalidOutput() {
        when(batchModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of()));

        assertKind(() -> llmService.complete(Pipeline.QUESTION_GENERATION, Mode.BATCH, "system", "user", ATTRIBUTION), MetisLlmException.Kind.INVALID_OUTPUT);
    }

    @Test
    void shouldBeUnavailableWithoutACallWhenTheEndpointIsNotConfigured() {
        MetisLlmService unconfigured = new MetisLlmService(new MetisConfiguration.MetisChatClients(null, null, ""), MetisTestProperties.defaults(), llmTokenUsageService);

        assertThat(unconfigured.isConfigured()).isFalse();
        assertKind(() -> unconfigured.complete(Pipeline.QUESTION_GENERATION, Mode.BATCH, "system", "user", ATTRIBUTION), MetisLlmException.Kind.UNAVAILABLE);
        assertKind(() -> unconfigured.complete(Pipeline.ANSWER_RATING, Mode.INTERACTIVE, "system", "user", ATTRIBUTION), MetisLlmException.Kind.UNAVAILABLE);
        verifyNoInteractions(llmTokenUsageService);
        assertThat(llmService.isConfigured()).isTrue();
    }

    @Test
    void shouldReportARefusedRequestAsRejected() {
        OpenAIServiceException tooLarge = statusException(413);
        when(batchModel.call(any(Prompt.class))).thenThrow(tooLarge);

        Throwable failure = catchThrowable(() -> llmService.complete(Pipeline.QUESTION_GENERATION, Mode.BATCH, "system", "user", ATTRIBUTION));

        assertThat(failure).isInstanceOf(MetisLlmException.class).hasRootCause(tooLarge);
        assertThat(((MetisLlmException) failure).getKind()).isEqualTo(MetisLlmException.Kind.REJECTED);
        verifyNoInteractions(llmTokenUsageService);
    }

    @Test
    void shouldReportAnOverloadedEndpointAsUnavailable() {
        when(batchModel.call(any(Prompt.class))).thenThrow(statusException(503));

        assertKind(() -> llmService.complete(Pipeline.QUESTION_GENERATION, Mode.BATCH, "system", "user", ATTRIBUTION), MetisLlmException.Kind.UNAVAILABLE);
    }

    @ParameterizedTest
    @ValueSource(ints = { 400, 413, 422 })
    void shouldClassifyARequestThatCanNeverSucceedAsRejected(int statusCode) {
        assertThat(MetisLlmService.classify(statusException(statusCode))).isEqualTo(MetisLlmException.Kind.REJECTED);
    }

    @ParameterizedTest
    @ValueSource(ints = { 401, 403, 404, 408, 409, 429, 500, 502, 503, 504 })
    void shouldClassifyEndpointAndConfigurationProblemsAsUnavailable(int statusCode) {
        assertThat(MetisLlmService.classify(statusException(statusCode))).isEqualTo(MetisLlmException.Kind.UNAVAILABLE);
    }

    @ParameterizedTest
    @MethodSource("transportFailures")
    void shouldClassifyTransportFailuresAndUnknownExceptionsAsUnavailable(Throwable failure) {
        assertThat(MetisLlmService.classify(failure)).isEqualTo(MetisLlmException.Kind.UNAVAILABLE);
    }

    private static Stream<Throwable> transportFailures() {
        return Stream.of(new SocketTimeoutException("Read timed out"), new OpenAIIoException("Request failed", new SocketTimeoutException("timeout")),
                new OpenAIRetryableException("Retryable"), new TimeoutException("Timed out"), new IllegalStateException("Something unexpected"));
    }

    @Test
    void shouldClassifyAlongTheCauseChain() {
        assertThat(MetisLlmService.classify(new RuntimeException("wrapper", statusException(422)))).isEqualTo(MetisLlmException.Kind.REJECTED);
        // the first exception that tells what happened decides
        assertThat(MetisLlmService.classify(new OpenAIIoException("Request failed", statusException(400)))).isEqualTo(MetisLlmException.Kind.UNAVAILABLE);
    }

    @Test
    void shouldBeUnavailableWhenNoPermitBecomesFreeInTime() throws Exception {
        llmService = llmService(MetisTestProperties.with(Map.of("llm.max-concurrent-calls", "1", "llm.interactive-permit-wait-seconds", "2")));
        CountDownLatch release = new CountDownLatch(1);
        Future<MetisLlmService.Completion> batch = holdTheOnlyPermit(release);

        Future<MetisLlmService.Completion> interactive = submit(() -> llmService.complete(Pipeline.ANSWER_RATING, Mode.INTERACTIVE, "system", "user", ATTRIBUTION));

        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(10)).until(() -> llmService.interactiveWaiting() == 1);
        assertKind(() -> getUnwrapped(interactive), MetisLlmException.Kind.UNAVAILABLE);
        assertThat(llmService.interactiveWaiting()).isZero();
        verify(interactiveModel, never()).call(any(Prompt.class));

        release.countDown();
        assertThat(batch.get(30, TimeUnit.SECONDS).text()).isEqualTo("batch");
    }

    @Test
    void shouldLetABatchCallWaitForAPermitWithoutTimeoutAndWithoutCountingItAsInteractive() throws Exception {
        llmService = llmService(MetisTestProperties.with(Map.of("llm.max-concurrent-calls", "1", "llm.interactive-permit-wait-seconds", "1")));
        CountDownLatch release = new CountDownLatch(1);
        Future<MetisLlmService.Completion> first = holdTheOnlyPermit(release);

        AtomicReference<Thread> caller = new AtomicReference<>();
        Future<MetisLlmService.Completion> second = submit(() -> {
            caller.set(Thread.currentThread());
            return llmService.complete(Pipeline.QUESTION_GENERATION, Mode.BATCH, "system", "user", ATTRIBUTION);
        });

        // parked on the semaphore without a timeout: a wait with a timeout would show as TIMED_WAITING
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(10)).until(() -> caller.get() != null && caller.get().getState() == Thread.State.WAITING);
        // twice the interactive permit wait: the batch call keeps waiting and never holds back batch work as a waiting interactive call
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).until(() -> !second.isDone() && llmService.interactiveWaiting() == 0);
        verify(batchModel, times(1)).call(any(Prompt.class));

        release.countDown();
        assertThat(first.get(30, TimeUnit.SECONDS).text()).isEqualTo("batch");
        assertThat(second.get(30, TimeUnit.SECONDS).text()).isEqualTo("batch");
        verify(batchModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void shouldPauseInteractiveCallsAfterThreeUnavailableResponsesInARow() {
        TimeUtil.setClock(Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));
        doThrow(statusException(503)).when(interactiveModel).call(any(Prompt.class));
        for (int attempt = 0; attempt < 3; attempt++) {
            assertKind(this::rateInteractively, MetisLlmException.Kind.UNAVAILABLE);
        }
        verify(interactiveModel, times(3)).call(any(Prompt.class));

        // the fourth call fails at once, without touching the model
        assertKind(this::rateInteractively, MetisLlmException.Kind.UNAVAILABLE);
        verify(interactiveModel, times(3)).call(any(Prompt.class));

        // batch calls are gated by the circuit of the dispatcher instead and still go out
        when(batchModel.call(any(Prompt.class))).thenReturn(response("batch", "model"));
        assertThat(llmService.complete(Pipeline.QUESTION_GENERATION, Mode.BATCH, "system", "user", ATTRIBUTION).text()).isEqualTo("batch");

        TimeUtil.setClock(Clock.fixed(NOW.plusSeconds(59).toInstant(), ZoneOffset.UTC));
        assertKind(this::rateInteractively, MetisLlmException.Kind.UNAVAILABLE);
        verify(interactiveModel, times(3)).call(any(Prompt.class));

        // after the pause the next interactive call probes the endpoint again
        TimeUtil.setClock(Clock.fixed(NOW.plusSeconds(60).toInstant(), ZoneOffset.UTC));
        doReturn(response("rated", "model")).when(interactiveModel).call(any(Prompt.class));
        assertThat(rateInteractively().text()).isEqualTo("rated");
        verify(interactiveModel, times(4)).call(any(Prompt.class));
    }

    @Test
    void shouldNotCountPermitTimeoutsTowardsThePause() throws Exception {
        llmService = llmService(MetisTestProperties.with(Map.of("llm.max-concurrent-calls", "1", "llm.interactive-permit-wait-seconds", "1")));
        CountDownLatch release = new CountDownLatch(1);
        Future<MetisLlmService.Completion> batch = holdTheOnlyPermit(release);

        List<Future<MetisLlmService.Completion>> waiting = new ArrayList<>();
        for (int call = 0; call < 3; call++) {
            waiting.add(submit(this::rateInteractively));
        }
        for (Future<MetisLlmService.Completion> call : waiting) {
            assertKind(() -> getUnwrapped(call), MetisLlmException.Kind.UNAVAILABLE);
        }
        release.countDown();
        batch.get(30, TimeUnit.SECONDS);

        when(interactiveModel.call(any(Prompt.class))).thenReturn(response("rated", "model"));
        assertThat(rateInteractively().text()).isEqualTo("rated");
    }

    @Test
    void shouldResetTheFailureCountWhenTheEndpointAnswers() {
        when(interactiveModel.call(any(Prompt.class))).thenThrow(statusException(503)).thenThrow(statusException(503)).thenReturn(response("rated", "model"))
                .thenThrow(statusException(503)).thenThrow(statusException(503)).thenThrow(statusException(400)).thenThrow(statusException(503)).thenThrow(statusException(503))
                .thenReturn(response("rated again", "model"));

        assertKind(this::rateInteractively, MetisLlmException.Kind.UNAVAILABLE);
        assertKind(this::rateInteractively, MetisLlmException.Kind.UNAVAILABLE);
        assertThat(rateInteractively().text()).isEqualTo("rated");
        assertKind(this::rateInteractively, MetisLlmException.Kind.UNAVAILABLE);
        assertKind(this::rateInteractively, MetisLlmException.Kind.UNAVAILABLE);
        // a refusal is an answer of the endpoint as well
        assertKind(this::rateInteractively, MetisLlmException.Kind.REJECTED);
        assertKind(this::rateInteractively, MetisLlmException.Kind.UNAVAILABLE);
        assertKind(this::rateInteractively, MetisLlmException.Kind.UNAVAILABLE);

        assertThat(rateInteractively().text()).isEqualTo("rated again");
        verify(interactiveModel, times(9)).call(any(Prompt.class));
    }

    @Test
    void shouldNotCountBatchFailuresTowardsTheInteractivePause() {
        when(batchModel.call(any(Prompt.class))).thenThrow(statusException(503));
        for (int attempt = 0; attempt < 3; attempt++) {
            assertKind(() -> llmService.complete(Pipeline.QUESTION_GENERATION, Mode.BATCH, "system", "user", ATTRIBUTION), MetisLlmException.Kind.UNAVAILABLE);
        }
        when(interactiveModel.call(any(Prompt.class))).thenReturn(response("rated", "model"));

        assertThat(rateInteractively().text()).isEqualTo("rated");
    }

    private MetisLlmService llmService(MetisProperties properties) {
        var clients = new MetisConfiguration.MetisChatClients(ChatClient.create(batchModel), ChatClient.create(interactiveModel), CONFIGURED_MODEL);
        return new MetisLlmService(clients, properties, llmTokenUsageService);
    }

    private MetisLlmService.Completion rateInteractively() {
        return llmService.complete(Pipeline.ANSWER_RATING, Mode.INTERACTIVE, "system", "user", ATTRIBUTION);
    }

    /**
     * Starts a batch call whose model call blocks until the latch is released, so that it holds a permit.
     *
     * @param release the latch that lets the model call return
     * @return the running batch call
     */
    private Future<MetisLlmService.Completion> holdTheOnlyPermit(CountDownLatch release) throws InterruptedException {
        CountDownLatch called = new CountDownLatch(1);
        when(batchModel.call(any(Prompt.class))).thenAnswer(_ -> {
            called.countDown();
            release.await(30, TimeUnit.SECONDS);
            return response("batch", "model");
        });
        Future<MetisLlmService.Completion> batch = submit(() -> llmService.complete(Pipeline.QUESTION_GENERATION, Mode.BATCH, "system", "user", ATTRIBUTION));
        assertThat(called.await(30, TimeUnit.SECONDS)).as("the batch call holds the permit").isTrue();
        return batch;
    }

    private Future<MetisLlmService.Completion> submit(Callable<MetisLlmService.Completion> call) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executors.add(executor);
        return executor.submit(call);
    }

    private static MetisLlmService.Completion getUnwrapped(Future<MetisLlmService.Completion> call) throws Exception {
        try {
            return call.get(30, TimeUnit.SECONDS);
        }
        catch (ExecutionException e) {
            if (e.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw e;
        }
    }

    private static void assertKind(ThrowingCallable call, MetisLlmException.Kind expectedKind) {
        assertThatThrownBy(call).isInstanceOfSatisfying(MetisLlmException.class, exception -> assertThat(exception.getKind()).isEqualTo(expectedKind));
    }

    private static ChatResponse response(String text, String model) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))), ChatResponseMetadata.builder().model(model).usage(new DefaultUsage(10, 20)).build());
    }

    private static OpenAIServiceException statusException(int statusCode) {
        return UnexpectedStatusCodeException.builder().statusCode(statusCode).headers(Headers.builder().build()).build();
    }
}
