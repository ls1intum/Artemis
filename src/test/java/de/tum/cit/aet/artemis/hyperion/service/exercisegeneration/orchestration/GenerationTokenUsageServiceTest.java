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
    void persistsCacheUsageAndOriginalRates() {
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
        var observed = new java.util.concurrent.atomic.AtomicReference<GenerationUsage>();
        assertThat(service.trackChatResponseTokenUsage(response, de.tum.cit.aet.artemis.admin.domain.LLMServiceType.HYPERION, "generation", builder -> builder, observed::set))
                .isTrue();
        org.mockito.ArgumentCaptor<java.util.List<de.tum.cit.aet.artemis.admin.domain.LLMRequest>> saved = org.mockito.ArgumentCaptor.captor();
        org.mockito.Mockito.verify(persistence).saveLLMTokenUsage(saved.capture(), org.mockito.ArgumentMatchers.eq(de.tum.cit.aet.artemis.admin.domain.LLMServiceType.HYPERION),
                org.mockito.ArgumentMatchers.any());
        assertThat(saved.getValue()).singleElement().satisfies(request -> {
            assertThat(request.numInputTokens()).isEqualTo(10);
            assertThat(request.numOutputTokens()).isEqualTo(3);
            assertThat(request.numCachedInputTokens()).isEqualTo(8);
            assertThat(request.costPerMillionInputToken()).isEqualTo(2f);
            assertThat(request.costPerMillionCachedInputToken()).isEqualTo(0.5f);
            assertThat(request.costPerMillionOutputToken()).isEqualTo(4f);
            assertThat(request.numCacheWriteInputTokens()).isZero();
            double persistedCost = ((request.numInputTokens() - request.numCachedInputTokens()) * request.costPerMillionInputToken()
                    + request.numCachedInputTokens() * request.costPerMillionCachedInputToken() + request.numOutputTokens() * request.costPerMillionOutputToken()) / 1_000_000.0;
            assertThat(persistedCost).isCloseTo(0.000020, org.assertj.core.api.Assertions.within(0.000000001));
            assertThat(persistedCost).isEqualTo(GenerationTokenUsageService.estimatedCostEur(observed.get()));
        });
        assertThat(observed.get().costPerMillionInputToken()).isEqualTo(2f);
        assertThat(observed.get().numCachedInputTokens()).isEqualTo(8L);
        assertThat(observed.get().costEstimateComplete()).isTrue();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({ "-1,2,0", "1,-2,0", "1,2,-1", "1,2,2" })
    void invalidProviderCountersCannotBePersistedOrObserved(int input, int output, long cached) {
        var usage = org.mockito.Mockito.mock(org.springframework.ai.chat.metadata.Usage.class);
        org.mockito.Mockito.when(usage.getPromptTokens()).thenReturn(input);
        org.mockito.Mockito.when(usage.getCompletionTokens()).thenReturn(output);
        org.mockito.Mockito.when(usage.getCacheReadInputTokens()).thenReturn(cached);
        var metadata = org.mockito.Mockito.mock(org.springframework.ai.chat.metadata.ChatResponseMetadata.class);
        org.mockito.Mockito.when(metadata.getUsage()).thenReturn(usage);
        var response = org.mockito.Mockito.mock(org.springframework.ai.chat.model.ChatResponse.class);
        org.mockito.Mockito.when(response.getMetadata()).thenReturn(metadata);
        var recorded = new java.util.ArrayList<GenerationUsage>();
        assertThat(
                llmTokenUsageService.trackChatResponseTokenUsage(response, de.tum.cit.aet.artemis.admin.domain.LLMServiceType.HYPERION, "PIPE", builder -> builder, recorded::add))
                .isFalse();
        assertThat(recorded).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(persistence);
    }

    @Test
    void missingResponseOrMetadataCannotBeMistakenForZeroTokenSpend() {
        var absentMetadata = org.mockito.Mockito.mock(org.springframework.ai.chat.model.ChatResponse.class);
        var absentUsage = org.mockito.Mockito.mock(org.springframework.ai.chat.model.ChatResponse.class);
        var metadata = org.mockito.Mockito.mock(org.springframework.ai.chat.metadata.ChatResponseMetadata.class);
        org.mockito.Mockito.when(absentUsage.getMetadata()).thenReturn(metadata);
        for (var response : java.util.Arrays.asList(null, absentMetadata, absentUsage)) {
            assertThat(GenerationTokenUsageService.billableTokens(response, 0.5)).isZero();
            assertThat(
                    llmTokenUsageService.trackChatResponseTokenUsage(response, de.tum.cit.aet.artemis.admin.domain.LLMServiceType.HYPERION, "PIPE", builder -> builder, ignored -> {
                        throw new AssertionError("missing usage must not be observed");
                    })).isFalse();
        }
        org.mockito.Mockito.verifyNoInteractions(persistence);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(value = { "NULL,0.5,12", "-10,0.5,12", "50,0.5,7", "5,0,7", "5,1,12", "5,-1,7", "5,2,12" }, nullValues = "NULL")
    void billableTokensClampCacheCountsAndWeightsWithoutUnderstatingUnreportedUsage(Long cached, double weight, long expected) {
        var usage = org.mockito.Mockito.mock(org.springframework.ai.chat.metadata.Usage.class);
        org.mockito.Mockito.when(usage.getPromptTokens()).thenReturn(10);
        org.mockito.Mockito.when(usage.getCompletionTokens()).thenReturn(2);
        org.mockito.Mockito.when(usage.getCacheReadInputTokens()).thenReturn(cached);
        var metadata = org.mockito.Mockito.mock(org.springframework.ai.chat.metadata.ChatResponseMetadata.class);
        org.mockito.Mockito.when(metadata.getUsage()).thenReturn(usage);
        var response = org.mockito.Mockito.mock(org.springframework.ai.chat.model.ChatResponse.class);
        org.mockito.Mockito.when(response.getMetadata()).thenReturn(metadata);
        assertThat(GenerationTokenUsageService.billableTokens(response, weight)).isEqualTo(expected);
    }

    @Test
    void observerFailureReportsIncompleteAccountingAfterDurablePersistence() {
        var metadata = org.springframework.ai.chat.metadata.ChatResponseMetadata.builder().model("gpt-5-mini").usage(new org.springframework.ai.chat.metadata.DefaultUsage(10, 2))
                .build();
        var response = new org.springframework.ai.chat.model.ChatResponse(java.util.List.of(), metadata);
        assertThat(llmTokenUsageService.trackChatResponseTokenUsage(response, de.tum.cit.aet.artemis.admin.domain.LLMServiceType.HYPERION, "PIPE", builder -> builder, ignored -> {
            throw new IllegalStateException("observer unavailable");
        })).isFalse();
        org.mockito.ArgumentCaptor<java.util.List<de.tum.cit.aet.artemis.admin.domain.LLMRequest>> requests = org.mockito.ArgumentCaptor.captor();
        org.mockito.Mockito.verify(persistence).saveLLMTokenUsage(requests.capture(), org.mockito.ArgumentMatchers.eq(de.tum.cit.aet.artemis.admin.domain.LLMServiceType.HYPERION),
                org.mockito.ArgumentMatchers.any());
        assertThat(requests.getValue()).hasSize(1);
    }

    @Test
    void zeroTokenUnknownModelIsCompleteAndOutputOnlyUsageNeedsOnlyOutputPrice() {
        assertThat(llmTokenUsageService.buildLLMRequest(null, 0, 0, "PIPE").costEstimateComplete()).isTrue();
        assertThat(llmTokenUsageService.buildLLMRequest("gpt-5-mini", 0, 10, "PIPE").costEstimateComplete()).isTrue();
        assertThat(llmTokenUsageService.buildLLMRequest("unknown", 0, 10, "PIPE").costEstimateComplete()).isFalse();
        var request = new GenerationUsage("model", 10, 2, 5, 4, "PIPE", null, null, 1, true);
        assertThat(GenerationTokenUsageService.estimatedCostEur(request)).isEqualTo(0.00004);
    }

}
