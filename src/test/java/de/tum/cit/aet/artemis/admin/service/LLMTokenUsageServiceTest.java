package de.tum.cit.aet.artemis.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

import de.tum.cit.aet.artemis.admin.domain.LLMRequest;
import de.tum.cit.aet.artemis.admin.domain.LLMServiceType;
import de.tum.cit.aet.artemis.core.config.LLMModelCostConfiguration;
import de.tum.cit.aet.artemis.core.test_repository.LLMTokenUsageRequestTestRepository;
import de.tum.cit.aet.artemis.core.test_repository.LLMTokenUsageTraceTestRepository;

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
        assertThat(request.costEstimateComplete()).isFalse();
    }

    @Test
    void explicitZeroRatesRepresentKnownFreeUsage() {
        LLMModelCostConfiguration configuration = new LLMModelCostConfiguration();
        LLMModelCostConfiguration.ModelCostProperties free = new LLMModelCostConfiguration.ModelCostProperties();
        free.setInputCostPerMillionEur(0f);
        free.setCachedInputCostPerMillionEur(0f);
        free.setOutputCostPerMillionEur(0f);
        configuration.setModelCosts(Map.of("local-model", free));
        LLMTokenUsageService service = new LLMTokenUsageService(llmTokenUsageTraceRepository, llmTokenUsageRequestRepository, configuration);

        assertThat(service.buildLLMRequest("local-model", 10, 5, "PIPE").costEstimateComplete()).isTrue();
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
    void trackChatResponseTokenUsage_withoutCompleteUsageMetadata_reportsFailure() {
        ChatResponse response = mock(ChatResponse.class);
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        Usage usage = mock(Usage.class);
        when(response.getMetadata()).thenReturn(metadata);
        when(metadata.getUsage()).thenReturn(usage);
        when(usage.getPromptTokens()).thenReturn(10);
        when(usage.getCompletionTokens()).thenReturn(null);

        assertThat(llmTokenUsageService.trackChatResponseTokenUsage(response, LLMServiceType.HYPERION, "PIPE", builder -> builder)).isFalse();
        verify(llmTokenUsageTraceRepository, never()).save(any());
    }

    @Test
    void trackChatResponseTokenUsage_whenPersistenceFails_reportsFailure() {
        ChatResponse response = mock(ChatResponse.class);
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        Usage usage = mock(Usage.class);
        when(response.getMetadata()).thenReturn(metadata);
        when(metadata.getUsage()).thenReturn(usage);
        when(metadata.getModel()).thenReturn("gpt-5-mini");
        when(usage.getPromptTokens()).thenReturn(10);
        when(usage.getCompletionTokens()).thenReturn(5);
        when(llmTokenUsageTraceRepository.save(any())).thenThrow(new IllegalStateException("database unavailable"));
        @SuppressWarnings("unchecked")
        Consumer<LLMRequest> observer = mock(Consumer.class);

        assertThat(llmTokenUsageService.trackChatResponseTokenUsage(response, LLMServiceType.HYPERION, "PIPE", builder -> builder, observer)).isFalse();
        verify(observer, never()).accept(any());
    }

    @Test
    void trackChatResponseTokenUsage_whenTransientObserverFails_reportsAnIncompleteAccountAfterPersistence() {
        ChatResponse response = mock(ChatResponse.class);
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        Usage usage = mock(Usage.class);
        when(response.getMetadata()).thenReturn(metadata);
        when(metadata.getUsage()).thenReturn(usage);
        when(metadata.getModel()).thenReturn("gpt-5-mini");
        when(usage.getPromptTokens()).thenReturn(10);
        when(usage.getCompletionTokens()).thenReturn(5);

        boolean complete = llmTokenUsageService.trackChatResponseTokenUsage(response, LLMServiceType.HYPERION, "PIPE", builder -> builder, request -> {
            throw new IllegalStateException("transient observer unavailable");
        });

        assertThat(complete).isFalse();
        verify(llmTokenUsageTraceRepository).save(any());
    }

    @Test
    void trackChatResponseTokenUsageExposesProviderMetadataToTransientObserver() {
        ChatResponse response = mock(ChatResponse.class);
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        Usage usage = mock(Usage.class);
        when(response.getMetadata()).thenReturn(metadata);
        when(metadata.getUsage()).thenReturn(usage);
        when(metadata.getModel()).thenReturn("gpt-5-mini");
        when(metadata.getId()).thenReturn("provider-id");
        when(usage.getPromptTokens()).thenReturn(10);
        when(usage.getCompletionTokens()).thenReturn(5);
        when(usage.getCacheReadInputTokens()).thenReturn(4L);
        AtomicReference<LLMRequest> observed = new AtomicReference<>();

        assertThat(llmTokenUsageService.trackChatResponseTokenUsage(response, LLMServiceType.HYPERION, "PIPE", builder -> builder, observed::set)).isTrue();
        assertThat(observed.get().providerRequestId()).isEqualTo("provider-id");
        assertThat(observed.get().numCachedInputTokens()).isEqualTo(4L);
        assertThat(observed.get().costEstimateComplete()).isTrue();
    }

    @Test
    void billableTokens_discountsWhatTheProviderServedFromItsCache() {
        // The observed generation: 2,472,800 of 2,960,916 input tokens were cache hits. At full weight that run exhausted a 3,000,000-token budget while having spent a
        // fraction of it, which is what a spend guard must not do.
        ChatResponse response = responseWithUsage(2_960_916, 40_813, 2_472_800L);

        assertThat(LLMTokenUsageService.totalTokens(response)).isEqualTo(3_001_729L);
        assertThat(LLMTokenUsageService.billableTokens(response, 0.5d)).isEqualTo(1_765_329L);
        assertThat(LLMTokenUsageService.billableTokens(response, 1.0d)).isEqualTo(LLMTokenUsageService.totalTokens(response));
    }

    @Test
    void billableTokens_withoutAReportedCacheSplit_chargesEveryInputTokenInFull() {
        // An unknown split must never understate spend, so it is treated as nothing having been cached.
        ChatResponse response = responseWithUsage(1_000, 100, null);

        assertThat(LLMTokenUsageService.billableTokens(response, 0.5d)).isEqualTo(1_100L);
    }

    @Test
    void billableTokens_clampsAnOutOfRangeWeightAndAnImpossibleCacheSplit() {
        // A provider reporting more cached tokens than prompt tokens, and a misconfigured weight, must not produce a negative or inflated charge.
        ChatResponse response = responseWithUsage(100, 10, 500L);

        assertThat(LLMTokenUsageService.billableTokens(response, 0d)).isEqualTo(10L);
        assertThat(LLMTokenUsageService.billableTokens(response, 5d)).isEqualTo(110L);
        assertThat(LLMTokenUsageService.billableTokens(response, -1d)).isEqualTo(10L);
    }

    @Test
    void billableTokens_withoutUsageMetadata_isZero() {
        assertThat(LLMTokenUsageService.billableTokens(null, 0.5d)).isZero();
    }

    private static ChatResponse responseWithUsage(int promptTokens, int completionTokens, Long cachedInputTokens) {
        ChatResponse response = mock(ChatResponse.class);
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        Usage usage = mock(Usage.class);
        when(response.getMetadata()).thenReturn(metadata);
        when(metadata.getUsage()).thenReturn(usage);
        when(usage.getPromptTokens()).thenReturn(promptTokens);
        when(usage.getCompletionTokens()).thenReturn(completionTokens);
        when(usage.getCacheReadInputTokens()).thenReturn(cachedInputTokens);
        return response;
    }

    private static LLMModelCostConfiguration createCostConfiguration() {
        LLMModelCostConfiguration costConfiguration = new LLMModelCostConfiguration();
        LLMModelCostConfiguration.ModelCostProperties modelCostProperties = new LLMModelCostConfiguration.ModelCostProperties();
        modelCostProperties.setInputCostPerMillionEur(0.23f);
        modelCostProperties.setCachedInputCostPerMillionEur(0.05f);
        modelCostProperties.setOutputCostPerMillionEur(1.84f);
        costConfiguration.setModelCosts(Map.of("gpt-5-mini", modelCostProperties));
        return costConfiguration;
    }
}
