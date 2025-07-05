package de.tum.cit.aet.artemis.iris.util;

import java.util.ArrayList;
import java.util.List;

import de.tum.cit.aet.artemis.core.domain.LLMRequest;

public final class IrisLLMMock {

    private static final String DEFAULT_MODEL = "test-llm";

    private static final int NUM_ENTRIES = 5;

    private static final int INPUT_TOKEN_BASE = 10;

    private static final float INPUT_COST_PER_MILLION = 0.5f;

    private static final int OUTPUT_TOKEN_BASE = 3;

    private static final float OUTPUT_COST_PER_MILLION = 0.12f;

    private static final int TOKEN_OFFSET = 5;

    private IrisLLMMock() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static List<LLMRequest> getMockLLMCosts(String pipelineId) {
        List<LLMRequest> costs = new ArrayList<>();
        for (int i = 0; i < NUM_ENTRIES; i++) {
            int inputTokens = i * INPUT_TOKEN_BASE + TOKEN_OFFSET;
            float inputCost = i * INPUT_COST_PER_MILLION;
            int outputTokens = i * OUTPUT_TOKEN_BASE + TOKEN_OFFSET;
            float outputCost = i * OUTPUT_COST_PER_MILLION;

            costs.add(new LLMRequest(DEFAULT_MODEL, inputTokens, inputCost, outputTokens, outputCost, pipelineId));
        }
        return costs;
    }
}
