package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.core.config.llm.LlmTestCatalog;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationTokenUsageService.GenerationUsage;

class GenerationTokenUsageServiceTest {

    @Mock
    private LLMTokenUsageService persistence;

    private GenerationTokenUsageService llmTokenUsageService;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        llmTokenUsageService = new GenerationTokenUsageService(persistence, LlmTestCatalog.priced("gpt-5-mini", LlmTestCatalog.price("gpt-5-mini", 0.25, null, null, 2.0)));
    }

    @Test
    void reportedZeroCacheHitsNeedNoCachePriceButUnknownOrPositiveHitsDo() {
        assertThat(llmTokenUsageService.buildLLMRequest(LlmTestCatalog.PRESET, "gpt-5-mini", 11, 7, "PIPE", null, 0L).costUsdComplete()).isTrue();
        assertThat(llmTokenUsageService.buildLLMRequest(LlmTestCatalog.PRESET, "gpt-5-mini", 11, 7, "PIPE", null, null).costUsdComplete()).isFalse();
        assertThat(llmTokenUsageService.buildLLMRequest(LlmTestCatalog.PRESET, "gpt-5-mini", 11, 7, "PIPE", null, 2L).costUsdComplete()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(doubles = { 0, 0.000123 })
    void reportedUsdCostWinsWithoutConfiguredPricesOrCacheSplit(double cost) {
        var response = de.tum.cit.aet.artemis.hyperion.runtime.agent.ProviderUsageTransport
                .restore(new de.tum.cit.aet.artemis.hyperion.protocol.ProviderUsageUpdate.Call("unknown", "request", 10, 2, null, cost), null);
        var observed = new java.util.concurrent.atomic.AtomicReference<GenerationUsage>();

        assertThat(llmTokenUsageService.trackChatResponseTokenUsage(response, de.tum.cit.aet.artemis.admin.domain.LLMServiceType.HYPERION, "generation", builder -> builder,
                observed::set)).isTrue();

        assertThat(observed.get().costUsd()).isEqualTo(cost);
        assertThat(observed.get().costUsdComplete()).isTrue();
        org.mockito.ArgumentCaptor<java.util.List<de.tum.cit.aet.artemis.admin.domain.LLMRequest>> saved = org.mockito.ArgumentCaptor.captor();
        org.mockito.Mockito.verify(persistence).saveLLMTokenUsage(saved.capture(), org.mockito.ArgumentMatchers.eq(de.tum.cit.aet.artemis.admin.domain.LLMServiceType.HYPERION),
                org.mockito.ArgumentMatchers.any());
        assertThat(saved.getValue()).singleElement().satisfies(request -> {
            assertThat(request.totalCostUsd()).isEqualTo(cost);
        });
    }

    @Test
    void missingCacheSplitOnlyContributesAKnownLowerBound() {
        var request = llmTokenUsageService.buildLLMRequest(LlmTestCatalog.PRESET, "gpt-5-mini", 100, 7, "generation", null, null);

        assertThat(request.costUsdComplete()).isFalse();
        assertThat(request.costUsd()).isEqualTo(14d / 1_000_000);
    }

    @Test
    void absentProviderUsageDoesNotCreateZeroCostRecord() {
        llmTokenUsageService.trackChatResponseTokenUsage(new org.springframework.ai.chat.model.ChatResponse(java.util.List.of()),
                de.tum.cit.aet.artemis.admin.domain.LLMServiceType.ATLAS, "ATLAS_ORCHESTRATION", builder -> builder.withCourse(1L), ignored -> {
                });
        org.mockito.Mockito.verifyNoInteractions(persistence);
    }

    @ParameterizedTest
    @CsvSource({ "true,false,3", "false,true,10", "true,true,0" })
    void missingTokenCountsPreventPersistenceButKeepKnownBudgetUsage(boolean missingInput, boolean missingOutput, long expectedBillableTokens) {
        var usage = org.mockito.Mockito.mock(org.springframework.ai.chat.metadata.Usage.class);
        org.mockito.Mockito.when(usage.getPromptTokens()).thenReturn(missingInput ? null : 10);
        org.mockito.Mockito.when(usage.getCompletionTokens()).thenReturn(missingOutput ? null : 3);
        var metadata = org.springframework.ai.chat.metadata.ChatResponseMetadata.builder().usage(usage).build();
        var response = new org.springframework.ai.chat.model.ChatResponse(java.util.List.of(), metadata);

        assertThat(llmTokenUsageService.trackChatResponseTokenUsage(response, de.tum.cit.aet.artemis.admin.domain.LLMServiceType.HYPERION, "generation", builder -> builder,
                ignored -> {
                })).isFalse();
        assertThat(GenerationTokenUsageService.billableTokens(response, 1)).isEqualTo(expectedBillableTokens);
        org.mockito.Mockito.verifyNoInteractions(persistence);
    }

    @Test
    void missingModelNameKeepsUsageButLeavesCostEstimateIncomplete() {
        var metadata = org.springframework.ai.chat.metadata.ChatResponseMetadata.builder().model(null).usage(new org.springframework.ai.chat.metadata.DefaultUsage(10, 3)).build();
        var response = new org.springframework.ai.chat.model.ChatResponse(java.util.List.of(), metadata);
        var observed = new java.util.concurrent.atomic.AtomicReference<GenerationUsage>();

        assertThat(llmTokenUsageService.trackChatResponseTokenUsage(response, de.tum.cit.aet.artemis.admin.domain.LLMServiceType.HYPERION, "generation", builder -> builder,
                observed::set)).isTrue();

        assertThat(observed.get().model()).isEmpty();
        assertThat(observed.get().numInputTokens()).isEqualTo(10);
        assertThat(observed.get().numOutputTokens()).isEqualTo(3);
        assertThat(observed.get().costUsdComplete()).isFalse();
    }

    @Test
    void buildLLMRequest_withDatedModelName_usesThePriceOfThePreset() {
        GenerationUsage request = llmTokenUsageService.buildLLMRequest(LlmTestCatalog.PRESET, "gpt-5-mini-2025-08-07", 11, 7, "PIPE", null, 0L);

        assertThat(request.model()).isEqualTo("gpt-5-mini-2025-08-07");
        assertThat(request.numInputTokens()).isEqualTo(11);
        assertThat(request.numOutputTokens()).isEqualTo(7);
        assertThat(request.costUsd()).isEqualTo(0.00001675);
        assertThat(request.pipelineId()).isEqualTo("PIPE");
    }

    @Test
    void buildLLMRequest_withoutPreset_remainsUnpriced() {
        GenerationUsage request = llmTokenUsageService.buildLLMRequest(null, "unknown-model-2025-08-07", 11, 7, "PIPE", null, 0L);

        assertThat(request.costUsd()).isZero();
        assertThat(request.costUsdComplete()).isFalse();
    }

    @Test
    void presetWithoutPriceLeavesTheEstimateIncomplete() {
        var service = new GenerationTokenUsageService(persistence, LlmTestCatalog.priced("gpt-5-mini", null));
        var request = service.buildLLMRequest(LlmTestCatalog.PRESET, "gpt-5-mini", 10, 3, "generation", null, 0L);
        assertThat(request.costUsdComplete()).isFalse();
    }

    @Test
    void persistsCacheUsageAndUsdEstimate() {
        var service = new GenerationTokenUsageService(persistence, LlmTestCatalog.priced("model", LlmTestCatalog.price("model", 2.0, 0.5, null, 4.0)));
        var response = org.mockito.Mockito.mock(org.springframework.ai.chat.model.ChatResponse.class);
        var metadata = org.mockito.Mockito.mock(org.springframework.ai.chat.metadata.ChatResponseMetadata.class);
        var usage = org.mockito.Mockito.mock(org.springframework.ai.chat.metadata.Usage.class);
        org.mockito.Mockito.when(response.getMetadata()).thenReturn(metadata);
        org.mockito.Mockito.when(metadata.getModel()).thenReturn("model");
        org.mockito.Mockito.when(metadata.get(de.tum.cit.aet.artemis.core.service.llm.LlmClientService.PRESET_METADATA_KEY)).thenReturn(LlmTestCatalog.PRESET);
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
            assertThat(request.numCacheWriteInputTokens()).isZero();
            double persistedCost = request.totalCostUsd();
            assertThat(persistedCost).isCloseTo(0.000020, org.assertj.core.api.Assertions.within(0.000000001));
            assertThat(persistedCost).isEqualTo(observed.get().costUsd());
        });
        assertThat(observed.get().numCachedInputTokens()).isEqualTo(8L);
        assertThat(observed.get().costUsdComplete()).isTrue();
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
        assertThat(llmTokenUsageService.buildLLMRequest(null, null, 0, 0, "PIPE", null, null).costUsdComplete()).isTrue();
        assertThat(llmTokenUsageService.buildLLMRequest(LlmTestCatalog.PRESET, "gpt-5-mini", 0, 10, "PIPE", null, 0L).costUsdComplete()).isTrue();
        assertThat(llmTokenUsageService.buildLLMRequest(null, "unknown", 0, 10, "PIPE", null, 0L).costUsdComplete()).isFalse();
    }

}
