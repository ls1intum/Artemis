package de.tum.cit.aet.artemis.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;

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
        llmTokenUsageService.trackChatResponseTokenUsage(new org.springframework.ai.chat.model.ChatResponse(java.util.List.of()),
                de.tum.cit.aet.artemis.admin.domain.LLMServiceType.ATLAS, "ATLAS_ORCHESTRATION", builder -> builder.withCourse(1L));
        org.mockito.Mockito.verifyNoInteractions(llmTokenUsageTraceRepository, llmTokenUsageRequestRepository);
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
        Logger logger = (Logger) LoggerFactory.getLogger(LLMTokenUsageService.class);
        ListAppender<ILoggingEvent> logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
        try {
            llmTokenUsageService.trackChatResponseTokenUsage(null, LLMServiceType.ATLAS, "NULL_RESPONSE", builder -> builder);

            assertThat(logAppender.list).filteredOn(event -> event.getLevel() == Level.WARN).extracting(ILoggingEvent::getFormattedMessage)
                    .anySatisfy(message -> assertThat(message).contains("NULL_RESPONSE", "chat response"));
            verifyNoInteractions(llmTokenUsageTraceRepository, llmTokenUsageRequestRepository);
        }
        finally {
            logger.detachAppender(logAppender);
            logAppender.stop();
        }
    }

    @Test
    void trackChatResponseTokenUsage_withValidUsage_persistsAccounting() {
        llmTokenUsageService.trackChatResponseTokenUsage(validResponse(), LLMServiceType.ATLAS, "ATLAS_ORCHESTRATION", builder -> builder.withCourse(42L));
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
        Logger logger = (Logger) LoggerFactory.getLogger(LLMTokenUsageService.class);
        ListAppender<ILoggingEvent> logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
        try {
            llmTokenUsageService.trackChatResponseTokenUsage(responseWithTokens(promptTokens, completionTokens), LLMServiceType.ATLAS, "INCOMPLETE_USAGE",
                    builder -> builder.withCourse(42L));

            assertThat(logAppender.list).filteredOn(event -> event.getLevel() == Level.WARN).extracting(ILoggingEvent::getFormattedMessage)
                    .anySatisfy(message -> assertThat(message).contains("INCOMPLETE_USAGE", "usage metadata is incomplete"));
            verifyNoInteractions(llmTokenUsageTraceRepository, llmTokenUsageRequestRepository);
        }
        finally {
            logger.detachAppender(logAppender);
            logAppender.stop();
        }
    }

    @Test
    void trackChatResponseTokenUsage_withReportedZeroTokenCounts_persistsAccounting() {
        llmTokenUsageService.trackChatResponseTokenUsage(responseWithTokens(0, 0), LLMServiceType.ATLAS, "ZERO_USAGE", builder -> builder.withCourse(42L));
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
        Logger logger = (Logger) LoggerFactory.getLogger(LLMTokenUsageService.class);
        ListAppender<ILoggingEvent> logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
        try {
            llmTokenUsageService.trackChatResponseTokenUsage(validResponse(), LLMServiceType.ATLAS, "ATLAS_ORCHESTRATION", builder -> builder.withCourse(42L));
            assertThat(logAppender.list).filteredOn(event -> event.getLevel() == Level.WARN).extracting(ILoggingEvent::getFormattedMessage)
                    .anySatisfy(message -> assertThat(message).contains("ATLAS_ORCHESTRATION", "database unavailable"));
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
