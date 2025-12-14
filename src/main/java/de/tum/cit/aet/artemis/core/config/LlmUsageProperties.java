package de.tum.cit.aet.artemis.core.config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Configuration holder for LLM cost estimates (per million tokens).
 */
@Component
@Lazy
@ConfigurationProperties(prefix = "spring.ai.llm")
public class LlmUsageProperties {

    private Map<String, ModelCost> costs = Map.of("gpt-5-mini", new ModelCost(0.23f, 1.84f));

    public Map<String, ModelCost> getCosts() {
        return costs;
    }

    public void setCosts(Map<String, ModelCost> costs) {
        this.costs = costs;
    }

    public static class ModelCost {

        private float costPerMillionInput;

        private float costPerMillionOutput;

        public ModelCost() {
        }

        public ModelCost(float costPerMillionInput, float costPerMillionOutput) {
            this.costPerMillionInput = costPerMillionInput;
            this.costPerMillionOutput = costPerMillionOutput;
        }

        public float getCostPerMillionInput() {
            return costPerMillionInput;
        }

        public void setCostPerMillionInput(float costPerMillionInput) {
            this.costPerMillionInput = costPerMillionInput;
        }

        public float getCostPerMillionOutput() {
            return costPerMillionOutput;
        }

        public void setCostPerMillionOutput(float costPerMillionOutput) {
            this.costPerMillionOutput = costPerMillionOutput;
        }
    }
}
