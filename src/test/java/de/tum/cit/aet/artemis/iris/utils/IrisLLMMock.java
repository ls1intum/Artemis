package de.tum.cit.aet.artemis.iris.utils;

import java.util.ArrayList;
import java.util.List;

import de.tum.cit.aet.artemis.core.domain.LLMRequest;

public final class IrisLLMMock {

    public static List<LLMRequest> getMockLLMCosts() {
        List<LLMRequest> costs = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            costs.add(new LLMRequest("test-llm", i * 10 + 5, i * 0.5f, i * 3 + 5, i * 0.12f, "IRIS_CHAT_EXERCISE_MESSAGE"));
        }
        return costs;
    }
}
