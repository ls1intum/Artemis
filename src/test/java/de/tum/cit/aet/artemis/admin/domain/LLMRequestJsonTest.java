package de.tum.cit.aet.artemis.admin.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.athena.dto.ResponseMetaDTO;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisChatStatusUpdateDTO;

class LLMRequestJsonTest {

    @Test
    void malformedCostDoesNotRejectIrisOrAthenaCallbacks() {
        String token = "{\"numInputTokens\":12,\"totalCostUsd\":{}}";
        var iris = JsonObjectMapper.get().readValue("{\"result\":\"ok\",\"tokens\":[" + token + "]}", PyrisChatStatusUpdateDTO.class);
        var athena = JsonObjectMapper.get().readValue("{\"llmRequests\":[" + token + "]}", ResponseMetaDTO.class);

        assertThat(iris.result()).isEqualTo("ok");
        assertThat(iris.tokens()).singleElement().satisfies(request -> assertThat(request.numInputTokens()).isEqualTo(12));
        assertThat(athena.llmRequests()).singleElement().satisfies(request -> assertThat(request.numInputTokens()).isEqualTo(12));
    }

    @ParameterizedTest
    @ValueSource(strings = { "\"bad\"", "{}", "[]", "-1", "true", "1e999" })
    void malformedOptionalCostDoesNotRejectTheTokenRecord(String cost) {
        LLMRequest request = JsonObjectMapper.get().readValue("{\"model\":\"test\",\"numInputTokens\":12,\"totalCostUsd\":" + cost + "}", LLMRequest.class);

        assertThat(request.numInputTokens()).isEqualTo(12);
        assertThat(request.totalCostUsd()).isNull();
    }

    @Test
    void explicitZeroUsdSurvivesDeserialization() {
        LLMRequest request = JsonObjectMapper.get().readValue("{\"totalCostUsd\":0}", LLMRequest.class);
        assertThat(request.totalCostUsd()).isZero();
    }

    @Test
    void readsPromptCacheUsageAndIgnoresLegacyRatesSentByPyris() {
        String json = """
                {"model": "gpt-6-luna", "numInputTokens": 1000, "costPerMillionInputToken": 0.1, "numOutputTokens": 20, "costPerMillionOutputToken": 0.5,
                 "pipelineId": "IRIS_CHAT_COURSE_MESSAGE", "numCachedInputTokens": 900, "costPerMillionCachedInputToken": 0.01,
                 "numCacheWriteInputTokens": 50, "costPerMillionCacheWriteInputToken": 0.125}
                """;

        LLMRequest request = JsonObjectMapper.get().readValue(json, LLMRequest.class);

        assertThat(request.numInputTokens()).isEqualTo(1000);
        assertThat(request.numCachedInputTokens()).isEqualTo(900);
        assertThat(request.numCacheWriteInputTokens()).isEqualTo(50);
        assertThat(request.totalCostUsd()).isNull();
    }

    @Test
    void readsUsageWithoutPromptCacheFieldsAsNoCacheUsage() {
        String json = """
                {"model": "gpt-5.4-mini", "numInputTokens": 10, "costPerMillionInputToken": 0.75, "numOutputTokens": 3, "costPerMillionOutputToken": 4.5,
                 "pipelineId": "IRIS_CHAT_COURSE_MESSAGE"}
                """;

        LLMRequest request = JsonObjectMapper.get().readValue(json, LLMRequest.class);

        assertThat(request.numInputTokens()).isEqualTo(10);
        assertThat(request.numCachedInputTokens()).isZero();
        assertThat(request.numCacheWriteInputTokens()).isZero();
    }
}
