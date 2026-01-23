package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;

/**
 * Configuration properties for LLM model costs used for usage tracking.
 * Configured via application.yml under {@code artemis.llm.model-costs}.
 */
@Profile(PROFILE_CORE)
@Configuration
@Lazy
@ConfigurationProperties(prefix = "artemis.llm")
public class LlmModelCostConfiguration {

    private Map<String, ModelCostProperties> modelCosts = new HashMap<>();

    public Map<String, ModelCostProperties> getModelCosts() {
        return modelCosts;
    }

    public void setModelCosts(Map<String, ModelCostProperties> modelCosts) {
        this.modelCosts = modelCosts;
    }

    /**
     * Properties for a single model's token costs in EUR.
     */
    public static class ModelCostProperties {

        private float inputCostPerMillionEur = 0f;

        private float outputCostPerMillionEur = 0f;

        public float getInputCostPerMillionEur() {
            return inputCostPerMillionEur;
        }

        public void setInputCostPerMillionEur(float inputCostPerMillionEur) {
            this.inputCostPerMillionEur = inputCostPerMillionEur;
        }

        public float getOutputCostPerMillionEur() {
            return outputCostPerMillionEur;
        }

        public void setOutputCostPerMillionEur(float outputCostPerMillionEur) {
            this.outputCostPerMillionEur = outputCostPerMillionEur;
        }
    }
}
