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
            requireValidPrice(key, "USD input", value.getInputCostPerMillionUsd());
            requireValidPrice(key, "USD output", value.getOutputCostPerMillionUsd());
            requireValidPrice(key, "USD cached input", value.getCachedInputCostPerMillionUsd());
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

    /** Explicit USD prices without seeded defaults. */
    public static class ModelCostProperties {

        @Nullable
        private Float inputCostPerMillionUsd;

        @Nullable
        private Float outputCostPerMillionUsd;

        @Nullable
        private Float cachedInputCostPerMillionUsd;

        @Nullable
        public Float getInputCostPerMillionUsd() {
            return inputCostPerMillionUsd;
        }

        public void setInputCostPerMillionUsd(float inputCostPerMillionUsd) {
            this.inputCostPerMillionUsd = inputCostPerMillionUsd;
        }

        @Nullable
        public Float getOutputCostPerMillionUsd() {
            return outputCostPerMillionUsd;
        }

        public void setOutputCostPerMillionUsd(float outputCostPerMillionUsd) {
            this.outputCostPerMillionUsd = outputCostPerMillionUsd;
        }

        @Nullable
        public Float getCachedInputCostPerMillionUsd() {
            return cachedInputCostPerMillionUsd;
        }

        public void setCachedInputCostPerMillionUsd(float cachedInputCostPerMillionUsd) {
            this.cachedInputCostPerMillionUsd = cachedInputCostPerMillionUsd;
        }

    }
}
