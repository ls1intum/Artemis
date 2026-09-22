package de.tum.cit.aet.artemis.hyperion.config;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import jakarta.annotation.PostConstruct;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Explicit generation prices, without shared-service defaults, under {@code artemis.llm.model-costs}. Missing prices remain unknown rather than silently becoming stale defaults.
 */
@Conditional(HyperionExerciseGenerationEnabled.class)
@Configuration
@Lazy
@ConfigurationProperties(prefix = "artemis.llm")
public class GenerationModelCostConfiguration {

    private static final Pattern NON_ALPHANUMERIC_PATTERN = Pattern.compile("[^a-zA-Z0-9]");

    private Map<String, ModelCostProperties> modelCosts = new HashMap<>();

    @PostConstruct
    void validateModelCosts() {
        modelCosts.forEach((key, value) -> {
            if (stripToAlphanumeric(key).isEmpty()) {
                throw new IllegalStateException("LLM model cost key '" + key + "' must contain at least one alphanumeric character");
            }
            requireValidPrice(key, "input", value.getInputCostPerMillionEur());
            requireValidPrice(key, "output", value.getOutputCostPerMillionEur());
            requireValidPrice(key, "cached input", value.getCachedInputCostPerMillionEur());
        });
    }

    private static void requireValidPrice(String model, String dimension, @Nullable Float price) {
        if (price != null && (!Float.isFinite(price) || price < 0)) {
            throw new IllegalStateException("LLM model cost for '" + model + "' has an invalid " + dimension + " price");
        }
    }

    /**
     * Normalizes model names to the form supported in environment-variable map keys.
     *
     * @param value the model name
     * @return the alphanumeric model name
     */
    public static String stripToAlphanumeric(String value) {
        return NON_ALPHANUMERIC_PATTERN.matcher(value).replaceAll("");
    }

    public Map<String, ModelCostProperties> getModelCosts() {
        return modelCosts;
    }

    public void setModelCosts(Map<String, ModelCostProperties> modelCosts) {
        this.modelCosts = modelCosts == null ? new HashMap<>() : modelCosts;
    }

    /** Properties for a single model's token costs in EUR. */
    public static class ModelCostProperties {

        @Nullable
        private Float inputCostPerMillionEur;

        @Nullable
        private Float outputCostPerMillionEur;

        @Nullable
        private Float cachedInputCostPerMillionEur;

        @Nullable
        public Float getInputCostPerMillionEur() {
            return inputCostPerMillionEur;
        }

        public void setInputCostPerMillionEur(float inputCostPerMillionEur) {
            this.inputCostPerMillionEur = inputCostPerMillionEur;
        }

        @Nullable
        public Float getOutputCostPerMillionEur() {
            return outputCostPerMillionEur;
        }

        public void setOutputCostPerMillionEur(float outputCostPerMillionEur) {
            this.outputCostPerMillionEur = outputCostPerMillionEur;
        }

        @Nullable
        public Float getCachedInputCostPerMillionEur() {
            return cachedInputCostPerMillionEur;
        }

        public void setCachedInputCostPerMillionEur(float cachedInputCostPerMillionEur) {
            this.cachedInputCostPerMillionEur = cachedInputCostPerMillionEur;
        }
    }
}
