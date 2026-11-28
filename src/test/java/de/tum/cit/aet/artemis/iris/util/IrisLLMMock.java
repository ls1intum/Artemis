package de.tum.cit.aet.artemis.iris.util;

import java.util.ArrayList;
import java.util.List;

import de.tum.cit.aet.artemis.admin.domain.LLMRequest;

public final class IrisLLMMock {

    private static final String DEFAULT_MODEL = "test-llm";

    private static final int NUM_ENTRIES = 5;

    private static final int INPUT_TOKEN_BASE = 10;

    private static final int OUTPUT_TOKEN_BASE = 3;

    private static final int TOKEN_OFFSET = 5;

    private static final int CACHED_TOKEN_BASE = 4;

    private static final int CACHE_WRITE_TOKEN_BASE = 2;

    private IrisLLMMock() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static List<LLMRequest> getMockLLMCosts(String pipelineId) {
        List<LLMRequest> costs = new ArrayList<>();
        for (int i = 0; i < NUM_ENTRIES; i++) {
            int inputTokens = i * INPUT_TOKEN_BASE + TOKEN_OFFSET;
            int outputTokens = i * OUTPUT_TOKEN_BASE + TOKEN_OFFSET;

            int cachedTokens = i * CACHED_TOKEN_BASE;
            int cacheWriteTokens = i * CACHE_WRITE_TOKEN_BASE;

            costs.add(new LLMRequest(DEFAULT_MODEL, inputTokens, outputTokens, pipelineId, cachedTokens, cacheWriteTokens, null));
        }
        return costs;
    }
}
