package de.tum.cit.aet.artemis.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.tum.cit.aet.artemis.admin.domain.LLMRequest;
import de.tum.cit.aet.artemis.admin.domain.LLMServiceType;
import de.tum.cit.aet.artemis.admin.domain.LLMTokenUsageTrace;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties;
import de.tum.cit.aet.artemis.core.config.llm.LlmTestCatalog;
import de.tum.cit.aet.artemis.core.service.llm.LlmClientService;
import de.tum.cit.aet.artemis.core.test_repository.LLMTokenUsageRequestTestRepository;
import de.tum.cit.aet.artemis.core.test_repository.LLMTokenUsageTraceTestRepository;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;

// Several tests assert log output through an appender on the service logger. A Spring context starting in parallel resets
// Logback and detaches that appender mid-test, so the class runs alone.
@Isolated
class LLMTokenUsageServiceTest {

    @Mock
    private LLMTokenUsageTraceTestRepository llmTokenUsageTraceRepository;

    @Mock
    private LLMTokenUsageRequestTestRepository llmTokenUsageRequestRepository;

    private LLMTokenUsageService llmTokenUsageService;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        llmTokenUsageService = service(0.25, null, null, 2.0);
    }

    @ParameterizedTest
    @ValueSource(strings = { "empty", "missingInput", "missingOutput" })
    void individualResponseWithoutCompleteCountersIsSavedAsUnpriced(String kind) {
        var metadata = ChatResponseMetadata.builder().model("gpt-5-mini");
        if (!kind.equals("empty")) {
            Usage usage = mock(Usage.class);
            when(usage.getPromptTokens()).thenReturn(kind.equals("missingInput") ? null : 10);
            when(usage.getCompletionTokens()).thenReturn(kind.equals("missingOutput") ? null : 2);
            metadata.usage(usage);
        }
        llmTokenUsageService.trackModelCallTokenUsage(new ChatResponse(List.of(), metadata.build()), LLMServiceType.ATLAS, "PIPE", scope -> scope.withCourse(42L));
        var trace = ArgumentCaptor.forClass(LLMTokenUsageTrace.class);
        verify(llmTokenUsageTraceRepository).save(trace.capture());
        assertThat(trace.getValue().getLLMRequests()).singleElement().satisfies(request -> {
            assertThat(request.getTotalCostUsd()).isNull();
            assertThat(request.getNumInputTokens()).isEqualTo(kind.equals("missingOutput") ? 10 : 0);
            assertThat(request.getNumOutputTokens()).isEqualTo(kind.equals("missingInput") ? 2 : 0);
        });
    }

    @Test
    void individualResponseAccountingFailureDoesNotRejectResponse() {
        when(llmTokenUsageTraceRepository.save(any(LLMTokenUsageTrace.class))).thenThrow(new IllegalStateException("storage unavailable"));
        llmTokenUsageService.trackModelCallTokenUsage(new ChatResponse(List.of()), LLMServiceType.ATLAS, "PIPE", scope -> scope.withCourse(42L));
        verify(llmTokenUsageTraceRepository).save(any(LLMTokenUsageTrace.class));
    }

    @Test
    void transportedCostTakesPrecedence() {
        var service = service(100.0, null, null, 200.0);
        var usage = new DefaultUsage(100, 20, 120, Map.of("prompt_tokens", 100, "completion_tokens", 20, "cost", 0.0001, "cost_currency", "USD"));
        var response = LlmTestCatalog.tagged(new ChatResponse(List.of(), ChatResponseMetadata.builder().model("gpt-5-mini").usage(usage).build()), LlmTestCatalog.PRESET);

        LLMRequest request = service.buildLLMRequest(response, "PIPE");
        assertThat(request).isNotNull();
        assertThat(request.totalCostUsd()).isEqualTo(0.0001);
    }

    @Test
    void missingProviderCostUsesThePriceOfThePresetsEndpoint() {
        LLMRequest estimated = service(1.0, null, null, 2.0).buildLLMRequest(response("gpt-5-mini", 1_000_000, 500_000), "PIPE");
        assertThat(estimated.totalCostUsd()).isEqualTo(2);
        var unpriced = new LLMTokenUsageService(llmTokenUsageTraceRepository, llmTokenUsageRequestRepository, LlmTestCatalog.priced("gpt-5-mini", null));
        assertThat(unpriced.buildLLMRequest(response("gpt-5-mini", 100, 20), "PIPE").totalCostUsd()).isNull();
    }

    @ParameterizedTest
    @CsvSource({ "1000,0,0.001", "0,1000,0.003", "400,600,0.0022" })
    void fullyCachedInputNeedsOnlyTheUsedCacheRates(long cached, long written, double expectedCost) {
        var service = service(null, 1.0, 3.0, null);
        var response = cachedResponse(cached, written);

        var request = service.buildLLMRequest(response, "CACHED");

        assertThat(request).isNotNull();
        assertThat(request.totalCostUsd()).isEqualTo(expectedCost);
    }

    @ParameterizedTest
    @CsvSource({ "1000,0,0.001", "0,1000,0.003" })
    void unusedNonfiniteRatesDoNotInvalidateTheUsedCachePrice(long cached, long written, double expectedCost) {
        var service = service(Double.NaN, cached > 0 ? 1.0 : Double.NaN, written > 0 ? 3.0 : Double.NaN, Double.NaN);
        var response = cachedResponse(cached, written);

        var request = service.buildLLMRequest(response, "CACHED");

        assertThat(request).isNotNull();
        assertThat(request.totalCostUsd()).isEqualTo(expectedCost);
    }

    @ParameterizedTest
    @CsvSource({ "999,0", "0,999", "400,599" })
    void uncachedInputStillNeedsTheRegularInputRate(long cached, long written) {
        var service = service(null, 1.0, 3.0, null);
        var response = cachedResponse(cached, written);

        var request = service.buildLLMRequest(response, "CACHED");

        assertThat(request).isNotNull();
        assertThat(request.totalCostUsd()).isNull();
    }

    @ParameterizedTest
    @ValueSource(doubles = { Double.NaN, Double.POSITIVE_INFINITY, -1 })
    void persistenceDiscardsInvalidUsdAmounts(double amount) {
        var incoming = new LLMRequest("test", 12, 3, "PIPE", 0, 0, amount);
        llmTokenUsageService.saveLLMTokenUsage(List.of(incoming), LLMServiceType.IRIS, builder -> builder);
        var saved = ArgumentCaptor.forClass(LLMTokenUsageTrace.class);
        verify(llmTokenUsageTraceRepository).save(saved.capture());
        assertThat(saved.getValue().getLLMRequests()).singleElement().satisfies(request -> {
            assertThat(request.getNumInputTokens()).isEqualTo(12);
            assertThat(request.getTotalCostUsd()).isNull();
        });
    }

    @Test
    void malformedExternalCostMetadataDoesNotRejectTokenPersistence() {
        var incoming = JsonObjectMapper.get().readValue("{\"numInputTokens\":12,\"totalCostUsd\":{}}", LLMRequest.class);
        llmTokenUsageService.saveLLMTokenUsage(List.of(incoming), LLMServiceType.IRIS, builder -> builder);
        verify(llmTokenUsageTraceRepository).save(any());
    }

    @Test
    void persistenceKeepsReportedZero() {
        var incoming = new LLMRequest("test", 12, 3, "PIPE", 0, 0, 0d);
        llmTokenUsageService.saveLLMTokenUsage(List.of(incoming), LLMServiceType.IRIS, builder -> builder);
        var saved = ArgumentCaptor.forClass(LLMTokenUsageTrace.class);
        verify(llmTokenUsageTraceRepository).save(saved.capture());
        assertThat(saved.getValue().getLLMRequests()).singleElement().satisfies(request -> {
            assertThat(request.getTotalCostUsd()).isZero();
        });
    }

    @Test
    void absentProviderUsageDoesNotCreateZeroCostRecord() {
        // a response without provider usage carries the empty usage, which is neither stored nor reported as a failure
        List<ILoggingEvent> warnings = warningsLoggedBy(() -> llmTokenUsageService.trackChatResponseTokenUsage(new ChatResponse(List.of()), LLMServiceType.ATLAS,
                "ATLAS_ORCHESTRATION", builder -> builder.withCourse(1L)));

        assertThat(warnings).isEmpty();
        verifyNoInteractions(llmTokenUsageTraceRepository, llmTokenUsageRequestRepository);
    }

    @Test
    void buildLLMRequest_withDatedModelName_usesThePriceOfThePreset() {
        // the provider answers with a dated snapshot name; the price is the one of the preset that made the call, not a lookup by that name
        LLMRequest request = llmTokenUsageService.buildLLMRequest(response("gpt-5-mini-2025-08-07", 11, 7), "PIPE");

        assertThat(request.model()).isEqualTo("gpt-5-mini-2025-08-07");
        assertThat(request.numInputTokens()).isEqualTo(11);
        assertThat(request.numOutputTokens()).isEqualTo(7);
        assertThat(request.totalCostUsd()).isEqualTo(0.00001675);
        assertThat(request.pipelineId()).isEqualTo("PIPE");
    }

    @Test
    void buildLLMRequest_withoutPreset_remainsUnpriced() {
        var untagged = new ChatResponse(List.of(), ChatResponseMetadata.builder().model("gpt-5-mini").usage(new DefaultUsage(11, 7)).build());

        assertThat(llmTokenUsageService.buildLLMRequest(untagged, "PIPE").totalCostUsd()).isNull();
    }

    @Test
    void buildLLMRequest_withUnknownPreset_remainsUnpriced() {
        var unknown = LlmTestCatalog.tagged(new ChatResponse(List.of(), ChatResponseMetadata.builder().model("gpt-5-mini").usage(new DefaultUsage(11, 7)).build()), "other");

        assertThat(llmTokenUsageService.buildLLMRequest(unknown, "PIPE").totalCostUsd()).isNull();
    }

    @Test
    void trackChatResponseTokenUsage_withMissingResponse_logsFailureWithoutPersistence() {
        List<ILoggingEvent> warnings = warningsLoggedBy(() -> llmTokenUsageService.trackChatResponseTokenUsage(null, LLMServiceType.ATLAS, "NULL_RESPONSE", builder -> builder));

        assertThat(warnings).singleElement().satisfies(event -> {
            assertThat(event.getFormattedMessage()).contains("NULL_RESPONSE", "chat response is missing");
            assertThat(event.getThrowableProxy()).as("a missing response is reported without a stack trace").isNull();
        });
        verifyNoInteractions(llmTokenUsageTraceRepository, llmTokenUsageRequestRepository);
    }

    @Test
    void trackChatResponseTokenUsage_withoutResponseMetadata_neverPropagatesAndDoesNotPersist() {
        // Spring AI never returns null metadata; an unstubbed mock does, and the failure must stay inside the tracking call
        ChatResponse response = mock(ChatResponse.class);
        AtomicReference<Throwable> thrown = new AtomicReference<>();

        List<ILoggingEvent> warnings = warningsLoggedBy(() -> {
            try {
                llmTokenUsageService.trackChatResponseTokenUsage(response, LLMServiceType.ATLAS, "NO_METADATA", builder -> builder.withCourse(42L));
            }
            catch (RuntimeException e) {
                thrown.set(e);
            }
        });

        assertThat(thrown.get()).isNull();
        assertThat(warnings).singleElement().satisfies(event -> {
            assertThat(event.getFormattedMessage()).contains("Failed to store token usage", "NO_METADATA");
            assertThat(event.getThrowableProxy().getClassName()).isEqualTo(NullPointerException.class.getName());
        });
        verifyNoInteractions(llmTokenUsageTraceRepository, llmTokenUsageRequestRepository);
    }

    @Test
    void trackChatResponseTokenUsage_withoutUsageInTheMetadata_neverPropagatesAndDoesNotPersist() {
        ChatResponse response = mock(ChatResponse.class);
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        when(response.getMetadata()).thenReturn(metadata);

        List<ILoggingEvent> warnings = warningsLoggedBy(
                () -> llmTokenUsageService.trackChatResponseTokenUsage(response, LLMServiceType.ATLAS, "NO_USAGE", builder -> builder.withCourse(42L)));

        assertThat(warnings).singleElement().satisfies(event -> {
            assertThat(event.getFormattedMessage()).contains("Failed to store token usage", "NO_USAGE");
            assertThat(event.getThrowableProxy().getClassName()).isEqualTo(NullPointerException.class.getName());
        });
        verifyNoInteractions(llmTokenUsageTraceRepository, llmTokenUsageRequestRepository);
    }

    @Test
    void trackChatResponseTokenUsage_withoutModelName_persistsWithEmptyModelAndThePresetPrice() {
        ChatResponse response = responseWithTokens(11, 7);
        when(response.getMetadata().getModel()).thenReturn(null);

        List<ILoggingEvent> warnings = warningsLoggedBy(
                () -> llmTokenUsageService.trackChatResponseTokenUsage(response, LLMServiceType.ATLAS, "NO_MODEL", builder -> builder.withCourse(42L)));

        assertThat(warnings).isEmpty();
        var saved = ArgumentCaptor.forClass(LLMTokenUsageTrace.class);
        verify(llmTokenUsageTraceRepository).save(saved.capture());
        assertThat(saved.getValue().getLLMRequests()).singleElement().satisfies(request -> {
            assertThat(request.getModel()).isEmpty();
            assertThat(request.getNumInputTokens()).isEqualTo(11);
            assertThat(request.getNumOutputTokens()).isEqualTo(7);
            assertThat(request.getTotalCostUsd()).as("the price comes from the preset, not from the reported model name").isEqualTo(0.00001675);
            assertThat(request.getServicePipelineId()).isEqualTo("NO_MODEL");
        });
    }

    @Test
    void trackChatResponseTokenUsage_withValidUsage_persistsAccounting() {
        List<ILoggingEvent> warnings = warningsLoggedBy(
                () -> llmTokenUsageService.trackChatResponseTokenUsage(validResponse(), LLMServiceType.ATLAS, "ATLAS_ORCHESTRATION", builder -> builder.withCourse(42L)));
        assertThat(warnings).isEmpty();
        var saved = ArgumentCaptor.forClass(LLMTokenUsageTrace.class);
        verify(llmTokenUsageTraceRepository).save(saved.capture());
        assertThat(saved.getValue().getCourseId()).isEqualTo(42L);
        assertThat(saved.getValue().getServiceType()).isEqualTo(LLMServiceType.ATLAS);
        assertThat(saved.getValue().getLLMRequests()).singleElement().satisfies(request -> {
            assertThat(request.getModel()).isEqualTo("gpt-5-mini");
            assertThat(request.getNumInputTokens()).isEqualTo(11);
            assertThat(request.getNumOutputTokens()).isEqualTo(7);
            assertThat(request.getTotalCostUsd()).isEqualTo(0.00001675);
            assertThat(request.getServicePipelineId()).isEqualTo("ATLAS_ORCHESTRATION");
        });
    }

    @ParameterizedTest
    @CsvSource(value = { "null, 7", "11, null", "null, null" }, nullValues = "null")
    void trackChatResponseTokenUsage_withMissingTokenCount_logsFailureWithoutPersistence(Integer promptTokens, Integer completionTokens) {
        List<ILoggingEvent> warnings = warningsLoggedBy(() -> llmTokenUsageService.trackChatResponseTokenUsage(responseWithTokens(promptTokens, completionTokens),
                LLMServiceType.ATLAS, "INCOMPLETE_USAGE", builder -> builder.withCourse(42L)));

        assertThat(warnings).singleElement().satisfies(event -> assertThat(event.getFormattedMessage()).contains("INCOMPLETE_USAGE", "usage metadata is incomplete",
                "prompt tokens: " + promptTokens, "completion tokens: " + completionTokens));
        verifyNoInteractions(llmTokenUsageTraceRepository, llmTokenUsageRequestRepository);
    }

    @Test
    void trackChatResponseTokenUsage_withReportedZeroTokenCounts_persistsAccounting() {
        List<ILoggingEvent> warnings = warningsLoggedBy(
                () -> llmTokenUsageService.trackChatResponseTokenUsage(responseWithTokens(0, 0), LLMServiceType.ATLAS, "ZERO_USAGE", builder -> builder.withCourse(42L)));
        assertThat(warnings).as("reported zeros are a valid usage, not a failure").isEmpty();
        var saved = ArgumentCaptor.forClass(LLMTokenUsageTrace.class);
        verify(llmTokenUsageTraceRepository).save(saved.capture());
        assertThat(saved.getValue().getLLMRequests()).singleElement().satisfies(request -> {
            assertThat(request.getNumInputTokens()).isZero();
            assertThat(request.getNumOutputTokens()).isZero();
            assertThat(request.getServicePipelineId()).isEqualTo("ZERO_USAGE");
        });
    }

    @Test
    void trackChatResponseTokenUsage_whenPersistenceFails_logsAndReturns() {
        when(llmTokenUsageTraceRepository.save(any())).thenThrow(new IllegalStateException("database unavailable"));

        List<ILoggingEvent> warnings = warningsLoggedBy(
                () -> llmTokenUsageService.trackChatResponseTokenUsage(validResponse(), LLMServiceType.ATLAS, "ATLAS_ORCHESTRATION", builder -> builder.withCourse(42L)));

        assertThat(warnings).singleElement().satisfies(event -> {
            assertThat(event.getFormattedMessage()).contains("ATLAS_ORCHESTRATION", "database unavailable");
            assertThat(event.getThrowableProxy().getClassName()).isEqualTo(IllegalStateException.class.getName());
        });
        verify(llmTokenUsageTraceRepository).save(any());
    }

    /**
     * Runs the action while collecting what the service logs at WARN level.
     */
    private static List<ILoggingEvent> warningsLoggedBy(Runnable action) {
        Logger logger = (Logger) LoggerFactory.getLogger(LLMTokenUsageService.class);
        ListAppender<ILoggingEvent> logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
        try {
            action.run();
            return logAppender.list.stream().filter(event -> event.getLevel() == Level.WARN).toList();
        }
        finally {
            logger.detachAppender(logAppender);
            logAppender.stop();
        }
    }

    private static ChatResponse validResponse() {
        return responseWithTokens(11, 7);
    }

    private static ChatResponse responseWithTokens(Integer promptTokens, Integer completionTokens) {
        ChatResponse response = mock(ChatResponse.class);
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        Usage usage = mock(Usage.class);
        when(response.getMetadata()).thenReturn(metadata);
        when(metadata.getUsage()).thenReturn(usage);
        when(metadata.getModel()).thenReturn("gpt-5-mini");
        when(metadata.get(LlmClientService.PRESET_METADATA_KEY)).thenReturn(LlmTestCatalog.PRESET);
        when(usage.getPromptTokens()).thenReturn(promptTokens);
        when(usage.getCompletionTokens()).thenReturn(completionTokens);
        return response;
    }

    private static ChatResponse response(String model, int input, int output) {
        return LlmTestCatalog.tagged(new ChatResponse(List.of(), ChatResponseMetadata.builder().model(model).usage(new DefaultUsage(input, output)).build()),
                LlmTestCatalog.PRESET);
    }

    private static ChatResponse cachedResponse(long cached, long written) {
        return LlmTestCatalog.tagged(
                new ChatResponse(List.of(), ChatResponseMetadata.builder().model("gpt-5-mini").usage(new DefaultUsage(1000, 0, 1000, null, cached, written)).build()),
                LlmTestCatalog.PRESET);
    }

    private LLMTokenUsageService service(Double input, Double cachedInput, Double cacheWrite, Double output) {
        var price = new LlmProperties.Price("gpt-5-mini", input, cachedInput, cacheWrite, output);
        return new LLMTokenUsageService(llmTokenUsageTraceRepository, llmTokenUsageRequestRepository, LlmTestCatalog.priced("gpt-5-mini", price));
    }
}
