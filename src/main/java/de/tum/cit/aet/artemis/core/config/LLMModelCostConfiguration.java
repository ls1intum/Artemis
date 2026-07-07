package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.HashMap;
import java.util.Map;

import jakarta.annotation.PostConstruct;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;

/**
 * Configuration properties for LLM model costs used for usage tracking, under {@code artemis.llm.model-costs}.
 * Baselines are seeded as code defaults so they survive a managed deployment that overrides the map (e.g. via
 * environment variables) and shadows the bundled YAML; configured entries always win.
 */
@Profile(PROFILE_CORE)
@Configuration
@Lazy
@ConfigurationProperties(prefix = "artemis.llm")
public class LLMModelCostConfiguration {

    private static final Map<String, ModelCostProperties> DEFAULT_MODEL_COSTS = Map.of("gpt-5.4", modelCost(2.30f, 13.80f), "gpt-5.4-mini", modelCost(0.69f, 4.14f));

    private Map<String, ModelCostProperties> modelCosts = new HashMap<>();

    @PostConstruct
    void applyDefaultModelCosts() {
        DEFAULT_MODEL_COSTS.forEach(modelCosts::putIfAbsent);
    }

    private static ModelCostProperties modelCost(float inputCostPerMillionEur, float outputCostPerMillionEur) {
        ModelCostProperties properties = new ModelCostProperties();
        properties.setInputCostPerMillionEur(inputCostPerMillionEur);
        properties.setOutputCostPerMillionEur(outputCostPerMillionEur);
        return properties;
    }

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
