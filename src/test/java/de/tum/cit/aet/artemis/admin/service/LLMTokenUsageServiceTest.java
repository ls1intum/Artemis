package de.tum.cit.aet.artemis.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.tum.cit.aet.artemis.admin.domain.LLMRequest;
import de.tum.cit.aet.artemis.admin.domain.LLMServiceType;
import de.tum.cit.aet.artemis.admin.domain.LLMTokenUsageTrace;
import de.tum.cit.aet.artemis.core.config.LLMModelCostConfiguration;
import de.tum.cit.aet.artemis.core.test_repository.LLMTokenUsageRequestTestRepository;
import de.tum.cit.aet.artemis.core.test_repository.LLMTokenUsageTraceTestRepository;

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
        llmTokenUsageService = new LLMTokenUsageService(llmTokenUsageTraceRepository, llmTokenUsageRequestRepository, createCostConfiguration());
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
    void buildLLMRequest_withDashedDateSuffix_usesConfiguredCost() {
        LLMRequest request = llmTokenUsageService.buildLLMRequest("gpt-5-mini-2025-08-07", 11, 7, "PIPE");

        assertThat(request.model()).isEqualTo("gpt-5-mini-2025-08-07");
        assertThat(request.numInputTokens()).isEqualTo(11);
        assertThat(request.numOutputTokens()).isEqualTo(7);
        assertThat(request.costPerMillionInputToken()).isEqualTo(0.23f);
        assertThat(request.costPerMillionOutputToken()).isEqualTo(1.84f);
        assertThat(request.pipelineId()).isEqualTo("PIPE");
    }

    @Test
    void buildLLMRequest_withDateSuffixWithoutSeparator_usesConfiguredCost() {
        LLMRequest request = llmTokenUsageService.buildLLMRequest("gpt-5-mini2025-08-07", 11, 7, "PIPE");

        assertThat(request.costPerMillionInputToken()).isEqualTo(0.23f);
        assertThat(request.costPerMillionOutputToken()).isEqualTo(1.84f);
    }

    @Test
    void buildLLMRequest_withDashlessVariant_usesStrippedFallback() {
        LLMRequest request = llmTokenUsageService.buildLLMRequest("gpt5mini-2025-08-07", 11, 7, "PIPE");

        assertThat(request.costPerMillionInputToken()).isEqualTo(0.23f);
        assertThat(request.costPerMillionOutputToken()).isEqualTo(1.84f);
    }

    @Test
    void buildLLMRequest_withDottedModel_andEnvStyleStrippedKey_usesStrippedFallback() {
        // Env-var configuration strips dots and dashes, so "gpt-5.4" is configured as the key "gpt54".
        // The runtime model name "gpt-5.4" must still resolve to that cost via the stripped fallback.
        LLMModelCostConfiguration configuration = new LLMModelCostConfiguration();
        LLMModelCostConfiguration.ModelCostProperties dottedModel = new LLMModelCostConfiguration.ModelCostProperties();
        dottedModel.setInputCostPerMillionEur(2.30f);
        dottedModel.setOutputCostPerMillionEur(13.80f);
        configuration.setModelCosts(Map.of("gpt54", dottedModel));
        LLMTokenUsageService service = new LLMTokenUsageService(llmTokenUsageTraceRepository, llmTokenUsageRequestRepository, configuration);

        LLMRequest request = service.buildLLMRequest("gpt-5.4", 11, 7, "PIPE");

        assertThat(request.costPerMillionInputToken()).isEqualTo(2.30f);
        assertThat(request.costPerMillionOutputToken()).isEqualTo(13.80f);
    }

    @Test
    void buildLLMRequest_withUnknownModel_returnsZeroCosts() {
        LLMRequest request = llmTokenUsageService.buildLLMRequest("unknown-model-2025-08-07", 11, 7, "PIPE");

        assertThat(request.costPerMillionInputToken()).isEqualTo(0.0f);
        assertThat(request.costPerMillionOutputToken()).isEqualTo(0.0f);
    }

    @Test
    void explicitCachePricesRemainSeparateInPersistence() {
        LLMRequest request = new LLMRequest("model", 11, 2f, 7, 5f, "PIPE", 2, 0.5f, 3, 4f);
        llmTokenUsageService.saveLLMTokenUsage(java.util.List.of(request), LLMServiceType.IRIS, builder -> builder);

        var saved = ArgumentCaptor.forClass(LLMTokenUsageTrace.class);
        verify(llmTokenUsageTraceRepository).save(saved.capture());
        assertThat(saved.getValue().getLLMRequests()).singleElement().satisfies(persisted -> {
            assertThat(persisted.getCostPerMillionInputTokens()).isEqualTo(2f);
            assertThat(persisted.getNumCachedInputTokens()).isEqualTo(2);
            assertThat(persisted.getCostPerMillionCachedInputTokens()).isEqualTo(0.5f);
            assertThat(persisted.getNumCacheWriteInputTokens()).isEqualTo(3);
            assertThat(persisted.getCostPerMillionCacheWriteInputTokens()).isEqualTo(4f);
        });
    }

    @Test
    void constructor_withStrippedModelCostCollision_throwsIllegalStateException() {
        LLMModelCostConfiguration configuration = new LLMModelCostConfiguration();
        LLMModelCostConfiguration.ModelCostProperties dashedModel = new LLMModelCostConfiguration.ModelCostProperties();
        dashedModel.setInputCostPerMillionEur(0.23f);
        dashedModel.setOutputCostPerMillionEur(1.84f);
        LLMModelCostConfiguration.ModelCostProperties dashlessModel = new LLMModelCostConfiguration.ModelCostProperties();
        dashlessModel.setInputCostPerMillionEur(0.10f);
        dashlessModel.setOutputCostPerMillionEur(0.20f);
        configuration.setModelCosts(Map.of("gpt-5-mini", dashedModel, "gpt5-mini", dashlessModel));

        assertThatThrownBy(() -> new LLMTokenUsageService(llmTokenUsageTraceRepository, llmTokenUsageRequestRepository, configuration)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("gpt-5-mini").hasMessageContaining("gpt5-mini").hasMessageContaining("gpt5mini");
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
    void trackChatResponseTokenUsage_withoutModelName_persistsWithEmptyModelAndZeroCost() {
        ChatResponse response = responseWithTokens(11, 7);
        when(response.getMetadata().getModel()).thenReturn(null);

        List<ILoggingEvent> warnings = warningsLoggedBy(
                () -> llmTokenUsageService.trackChatResponseTokenUsage(response, LLMServiceType.ATLAS, "NO_MODEL", builder -> builder.withCourse(42L)));

        assertThat(warnings).singleElement().satisfies(event -> assertThat(event.getFormattedMessage()).contains("No LLM cost configured for model ''", "NO_MODEL"));
        var saved = ArgumentCaptor.forClass(LLMTokenUsageTrace.class);
        verify(llmTokenUsageTraceRepository).save(saved.capture());
        assertThat(saved.getValue().getLLMRequests()).singleElement().satisfies(request -> {
            assertThat(request.getModel()).isEmpty();
            assertThat(request.getNumInputTokens()).isEqualTo(11);
            assertThat(request.getNumOutputTokens()).isEqualTo(7);
            assertThat(request.getCostPerMillionInputTokens()).isZero();
            assertThat(request.getCostPerMillionOutputTokens()).isZero();
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
            assertThat(request.getCostPerMillionInputTokens()).isEqualTo(0.23f);
            assertThat(request.getCostPerMillionOutputTokens()).isEqualTo(1.84f);
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
        when(usage.getPromptTokens()).thenReturn(promptTokens);
        when(usage.getCompletionTokens()).thenReturn(completionTokens);
        return response;
    }

    private static LLMModelCostConfiguration createCostConfiguration() {
        LLMModelCostConfiguration costConfiguration = new LLMModelCostConfiguration();
        LLMModelCostConfiguration.ModelCostProperties modelCostProperties = new LLMModelCostConfiguration.ModelCostProperties();
        modelCostProperties.setInputCostPerMillionEur(0.23f);
        modelCostProperties.setOutputCostPerMillionEur(1.84f);
        costConfiguration.setModelCosts(Map.of("gpt-5-mini", modelCostProperties));
        return costConfiguration;
    }
}
