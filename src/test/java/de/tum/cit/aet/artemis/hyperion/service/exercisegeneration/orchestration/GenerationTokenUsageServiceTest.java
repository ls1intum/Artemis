package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.hyperion.config.GenerationModelCostConfiguration;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationTokenUsageService.GenerationUsage;

class GenerationTokenUsageServiceTest {

    @Mock
    private LLMTokenUsageService persistence;

    private GenerationTokenUsageService llmTokenUsageService;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        llmTokenUsageService = new GenerationTokenUsageService(persistence, createCostConfiguration());
    }

    @Test
    void reportedZeroCacheHitsNeedNoCachePriceButUnknownOrPositiveHitsDo() {
        assertThat(llmTokenUsageService.buildLLMRequest("gpt-5-mini", 11, 7, "PIPE", null, 0L).costEstimateComplete()).isTrue();
        assertThat(llmTokenUsageService.buildLLMRequest("gpt-5-mini", 11, 7, "PIPE", null, null).costEstimateComplete()).isFalse();
        assertThat(llmTokenUsageService.buildLLMRequest("gpt-5-mini", 11, 7, "PIPE", null, 2L).costEstimateComplete()).isFalse();
    }

    @Test
    void absentProviderUsageDoesNotCreateZeroCostRecord() {
        llmTokenUsageService.trackChatResponseTokenUsage(new org.springframework.ai.chat.model.ChatResponse(java.util.List.of()),
                de.tum.cit.aet.artemis.admin.domain.LLMServiceType.ATLAS, "ATLAS_ORCHESTRATION", builder -> builder.withCourse(1L), ignored -> {
                });
        org.mockito.Mockito.verifyNoInteractions(persistence);
    }

    @Test
    void buildLLMRequest_withDashedDateSuffix_usesConfiguredCost() {
        GenerationUsage request = llmTokenUsageService.buildLLMRequest("gpt-5-mini-2025-08-07", 11, 7, "PIPE");

        assertThat(request.model()).isEqualTo("gpt-5-mini-2025-08-07");
        assertThat(request.numInputTokens()).isEqualTo(11);
        assertThat(request.numOutputTokens()).isEqualTo(7);
        assertThat(request.costPerMillionInputToken()).isEqualTo(0.23f);
        assertThat(request.costPerMillionOutputToken()).isEqualTo(1.84f);
        assertThat(request.pipelineId()).isEqualTo("PIPE");
    }

    @Test
    void buildLLMRequest_withDateSuffixWithoutSeparator_usesConfiguredCost() {
        GenerationUsage request = llmTokenUsageService.buildLLMRequest("gpt-5-mini2025-08-07", 11, 7, "PIPE");

        assertThat(request.costPerMillionInputToken()).isEqualTo(0.23f);
        assertThat(request.costPerMillionOutputToken()).isEqualTo(1.84f);
    }

    @Test
    void buildLLMRequest_withDashlessVariant_usesStrippedFallback() {
        GenerationUsage request = llmTokenUsageService.buildLLMRequest("gpt5mini-2025-08-07", 11, 7, "PIPE");

        assertThat(request.costPerMillionInputToken()).isEqualTo(0.23f);
        assertThat(request.costPerMillionOutputToken()).isEqualTo(1.84f);
    }

    @Test
    void buildLLMRequest_withDottedModel_andEnvStyleStrippedKey_usesStrippedFallback() {
        // Env-var configuration strips dots and dashes, so "gpt-5.4" is configured as the key "gpt54".
        // The runtime model name "gpt-5.4" must still resolve to that cost via the stripped fallback.
        GenerationModelCostConfiguration configuration = new GenerationModelCostConfiguration();
        GenerationModelCostConfiguration.ModelCostProperties dottedModel = new GenerationModelCostConfiguration.ModelCostProperties();
        dottedModel.setInputCostPerMillionEur(2.30f);
        dottedModel.setOutputCostPerMillionEur(13.80f);
        configuration.setModelCosts(Map.of("gpt54", dottedModel));
        GenerationTokenUsageService service = new GenerationTokenUsageService(persistence, configuration);

        GenerationUsage request = service.buildLLMRequest("gpt-5.4", 11, 7, "PIPE");

        assertThat(request.costPerMillionInputToken()).isEqualTo(2.30f);
        assertThat(request.costPerMillionOutputToken()).isEqualTo(13.80f);
    }

    @Test
    void buildLLMRequest_withUnknownModel_returnsZeroCosts() {
        GenerationUsage request = llmTokenUsageService.buildLLMRequest("unknown-model-2025-08-07", 11, 7, "PIPE");

        assertThat(request.costPerMillionInputToken()).isEqualTo(0.0f);
        assertThat(request.costPerMillionOutputToken()).isEqualTo(0.0f);
    }

    @Test
    void constructor_withStrippedModelCostCollision_throwsIllegalStateException() {
        GenerationModelCostConfiguration configuration = new GenerationModelCostConfiguration();
        GenerationModelCostConfiguration.ModelCostProperties dashedModel = new GenerationModelCostConfiguration.ModelCostProperties();
        dashedModel.setInputCostPerMillionEur(0.23f);
        dashedModel.setOutputCostPerMillionEur(1.84f);
        GenerationModelCostConfiguration.ModelCostProperties dashlessModel = new GenerationModelCostConfiguration.ModelCostProperties();
        dashlessModel.setInputCostPerMillionEur(0.10f);
        dashlessModel.setOutputCostPerMillionEur(0.20f);
        configuration.setModelCosts(Map.of("gpt-5-mini", dashedModel, "gpt5-mini", dashlessModel));

        assertThatThrownBy(() -> new GenerationTokenUsageService(persistence, configuration)).isInstanceOf(IllegalStateException.class).hasMessageContaining("gpt-5-mini")
                .hasMessageContaining("gpt5-mini").hasMessageContaining("gpt5mini");
    }

    private static GenerationModelCostConfiguration createCostConfiguration() {
        GenerationModelCostConfiguration costConfiguration = new GenerationModelCostConfiguration();
        GenerationModelCostConfiguration.ModelCostProperties modelCostProperties = new GenerationModelCostConfiguration.ModelCostProperties();
        modelCostProperties.setInputCostPerMillionEur(0.23f);
        modelCostProperties.setOutputCostPerMillionEur(1.84f);
        costConfiguration.setModelCosts(Map.of("gpt-5-mini", modelCostProperties));
        return costConfiguration;
    }

    @Test
    void sharedDefaultsAreNotGenerationPrices() {
        var service = new GenerationTokenUsageService(persistence, new GenerationModelCostConfiguration());
        var request = service.buildLLMRequest("gpt-5.4", 10, 3, "generation", null, 0L);
        assertThat(request.costEstimateComplete()).isFalse();
        assertThat(request.costPerMillionInputToken()).isZero();
        assertThat(request.costPerMillionOutputToken()).isZero();
    }

    @Test
    void persistsBlendedCacheCostButReportsOriginalRates() {
        var config = new GenerationModelCostConfiguration();
        var rate = new GenerationModelCostConfiguration.ModelCostProperties();
        rate.setInputCostPerMillionEur(2f);
        rate.setOutputCostPerMillionEur(4f);
        rate.setCachedInputCostPerMillionEur(0.5f);
        config.setModelCosts(Map.of("model", rate));
        var service = new GenerationTokenUsageService(persistence, config);
        var response = org.mockito.Mockito.mock(org.springframework.ai.chat.model.ChatResponse.class);
        var metadata = org.mockito.Mockito.mock(org.springframework.ai.chat.metadata.ChatResponseMetadata.class);
        var usage = org.mockito.Mockito.mock(org.springframework.ai.chat.metadata.Usage.class);
        org.mockito.Mockito.when(response.getMetadata()).thenReturn(metadata);
        org.mockito.Mockito.when(metadata.getModel()).thenReturn("model");
        org.mockito.Mockito.when(metadata.getUsage()).thenReturn(usage);
        org.mockito.Mockito.when(usage.getPromptTokens()).thenReturn(10);
        org.mockito.Mockito.when(usage.getCompletionTokens()).thenReturn(3);
        org.mockito.Mockito.when(usage.getCacheReadInputTokens()).thenReturn(8L);
        org.mockito.Mockito.doAnswer(invocation -> {
            java.util.List<de.tum.cit.aet.artemis.admin.domain.LLMRequest> requests = invocation.getArgument(0);
            assertThat(requests.getFirst().costPerMillionInputToken()).isCloseTo(0.8f, org.assertj.core.api.Assertions.within(0.00001f));
            return null;
        }).when(persistence).saveLLMTokenUsage(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        var observed = new java.util.concurrent.atomic.AtomicReference<GenerationUsage>();
        assertThat(service.trackChatResponseTokenUsage(response, de.tum.cit.aet.artemis.admin.domain.LLMServiceType.HYPERION, "generation", builder -> builder, observed::set))
                .isTrue();
        assertThat(observed.get().costPerMillionInputToken()).isEqualTo(2f);
        assertThat(observed.get().numCachedInputTokens()).isEqualTo(8L);
        assertThat(observed.get().costEstimateComplete()).isTrue();
    }
}
