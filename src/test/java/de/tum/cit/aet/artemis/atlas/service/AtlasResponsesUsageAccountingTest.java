package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Map;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.admin.repository.LLMTokenUsageRequestRepository;
import de.tum.cit.aet.artemis.admin.repository.LLMTokenUsageTraceRepository;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties;
import de.tum.cit.aet.artemis.core.config.llm.LlmTestCatalog;
import de.tum.cit.aet.artemis.core.service.llm.OpenAiResponsesTestFixtures;

/** Verifies JSON decoding, Responses adapter and configured USD accounting together. */
class AtlasResponsesUsageAccountingTest {

    @ParameterizedTest
    @CsvSource({ "2000,0,0.002", "0,2000,0.004", "1200,300,0.0068", "0,0,0.02" })
    void configuredEstimateUsesTheReportedCacheSplit(long cached, long written, double expected) throws Exception {
        String details = "{\"cached_tokens\":" + cached + ",\"cache_write_tokens\":" + written + "}";
        ChatResponse response = response(details, null);

        var request = accounting(rates()).buildLLMRequest(response, "CACHE_TEST");

        assertThat(response.getMetadata().getUsage().getPromptTokens()).isEqualTo(2000);
        assertThat(response.getMetadata().getUsage().getCacheReadInputTokens()).isEqualTo(cached);
        assertThat(response.getMetadata().getUsage().getCacheWriteInputTokens()).isEqualTo(written);
        assertThat(response.getMetadata().getUsage().getNativeUsage()).isInstanceOf(Map.class);
        assertThat(request).isNotNull();
        assertThat(request.numInputTokens()).isEqualTo(2000);
        assertThat(request.numCachedInputTokens()).isEqualTo((int) cached);
        assertThat(request.numCacheWriteInputTokens()).isEqualTo((int) written);
        assertThat(request.totalCostUsd()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(doubles = { 0, 0.123 })
    void reportedProviderAmountStillWinsOverTheCacheEstimate(double amount) throws Exception {
        var response = response("{\"cached_tokens\":1200,\"cache_write_tokens\":300}", amount);

        var request = accounting(rates()).buildLLMRequest(response, "CACHE_TEST");

        assertThat(request).isNotNull();
        assertThat(request.numCachedInputTokens()).isEqualTo(1200);
        assertThat(request.numCacheWriteInputTokens()).isEqualTo(300);
        assertThat(request.totalCostUsd()).isEqualTo(amount);
    }

    @ParameterizedTest
    @MethodSource("optionalCacheDetails")
    void missingOptionalCacheFieldsPreserveTheKnownCounters(@Nullable String details, @Nullable Long cached, @Nullable Long written, double expected) throws Exception {
        var response = response(details, null);

        var request = accounting(rates()).buildLLMRequest(response, "CACHE_TEST");

        assertThat(response.getMetadata().getUsage().getCacheReadInputTokens()).isEqualTo(cached);
        assertThat(response.getMetadata().getUsage().getCacheWriteInputTokens()).isEqualTo(written);
        assertThat(request).isNotNull();
        assertThat(request.totalCostUsd()).isEqualTo(expected);
    }

    private static Stream<Arguments> optionalCacheDetails() {
        return Stream.of(Arguments.of(null, null, null, 0.02), Arguments.of("null", null, null, 0.02), Arguments.of("{}", null, null, 0.02),
                Arguments.of("{\"cached_tokens\":null,\"cache_write_tokens\":null}", null, null, 0.02), Arguments.of("{\"cached_tokens\":1200}", 1200L, null, 0.0092),
                Arguments.of("{\"cache_write_tokens\":300}", null, 300L, 0.0176), Arguments.of("{\"cached_tokens\":1200,\"cache_write_tokens\":null}", 1200L, null, 0.0092));
    }

    @ParameterizedTest
    @ValueSource(strings = { "null", "\"bad\"", "[]", "{\"cached_tokens\":\"bad\",\"cache_write_tokens\":300}" })
    void malformedOptionalCacheDataDoesNotDiscardAProviderAmount(String details) throws Exception {
        var response = response(details, 0.007);

        var request = accounting(rates()).buildLLMRequest(response, "CACHE_TEST");

        assertThat(response.getMetadata().getUsage().getPromptTokens()).isEqualTo(2000);
        assertThat(request).isNotNull();
        assertThat(request.totalCostUsd()).isEqualTo(0.007);
    }

    @Test
    void fullyCachedInputNeedsNoUnusedRegularInputOrOutputRate() throws Exception {
        var configured = new LlmProperties.Price("cache-model", null, 1.0, 2.0, null);

        var request = accounting(configured).buildLLMRequest(response("{\"cached_tokens\":2000}", null), "CACHE_TEST");

        assertThat(request).isNotNull();
        assertThat(request.numCachedInputTokens()).isEqualTo(2000);
        assertThat(request.totalCostUsd()).isEqualTo(0.002);
    }

    private static ChatResponse response(@Nullable String details, @Nullable Double amount) throws Exception {
        String detailsField = details == null ? "" : ",\"input_tokens_details\":" + details;
        String costField = amount == null ? "" : ",\"cost\":" + amount + ",\"cost_currency\":\"USD\"";
        String json = """
                {"id":"resp-cache","model":"cache-model","status":"completed","output":[],
                 "usage":{"input_tokens":2000,"output_tokens":0,"total_tokens":2000%s%s}}
                """.formatted(detailsField, costField);
        Map<String, Object> reply = new JsonMapper().readValue(json, new TypeReference<>() {
        });
        var model = OpenAiResponsesTestFixtures.model("cache-model", request -> reply);
        return LlmTestCatalog.tagged(model.call(new Prompt("accounting")), LlmTestCatalog.PRESET);
    }

    private static LlmProperties.Price rates() {
        return new LlmProperties.Price("cache-model", 10.0, 1.0, 2.0, 20.0);
    }

    private static LLMTokenUsageService accounting(LlmProperties.Price rates) {
        return new LLMTokenUsageService(mock(LLMTokenUsageTraceRepository.class), mock(LLMTokenUsageRequestRepository.class), LlmTestCatalog.priced("cache-model", rates));
    }
}
