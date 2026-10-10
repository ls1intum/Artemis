package de.tum.cit.aet.artemis.admin.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;

class LLMRequestJsonTest {

    @Test
    void readsPromptCacheUsageSentByPyris() {
        String json = """
                {"model": "gpt-6-luna", "numInputTokens": 1000, "costPerMillionInputToken": 0.1, "numOutputTokens": 20, "costPerMillionOutputToken": 0.5,
                 "pipelineId": "IRIS_CHAT_COURSE_MESSAGE", "numCachedInputTokens": 900, "costPerMillionCachedInputToken": 0.01,
                 "numCacheWriteInputTokens": 50, "costPerMillionCacheWriteInputToken": 0.125}
                """;

        LLMRequest request = JsonObjectMapper.get().readValue(json, LLMRequest.class);

        assertThat(request.numInputTokens()).isEqualTo(1000);
        assertThat(request.numCachedInputTokens()).isEqualTo(900);
        assertThat(request.costPerMillionCachedInputToken()).isEqualTo(0.01f);
        assertThat(request.numCacheWriteInputTokens()).isEqualTo(50);
        assertThat(request.costPerMillionCacheWriteInputToken()).isEqualTo(0.125f);
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
        assertThat(request.costPerMillionCachedInputToken()).isZero();
        assertThat(request.numCacheWriteInputTokens()).isZero();
        assertThat(request.costPerMillionCacheWriteInputToken()).isZero();
    }
}
