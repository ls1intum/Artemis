package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

    private static final Map<String, ModelCostProperties> DEFAULT_MODEL_COSTS = Map.of("gpt-5-mini", modelCost(0.25f, 2f, 0.025f), "gpt-5.4", modelCost(2.50f, 15f, 0.25f),
            "gpt-5.4-mini", modelCost(0.75f, 4.50f, 0.075f));

    private static final Pattern NON_ALPHANUMERIC_PATTERN = Pattern.compile("[^a-zA-Z0-9]");

    private Map<String, ModelCostProperties> modelCosts = new HashMap<>();

    @PostConstruct
    void applyDefaultModelCosts() {
        // Reject keys that carry no alphanumeric characters: they strip to "", which is also the form a
        // missing model name takes in LLMTokenUsageService, so they would silently become the fallback
        // price for every model-less request. Fail fast before such a key can reach the lookup maps.
        modelCosts.keySet().forEach(key -> {
            if (stripToAlphanumeric(key).isEmpty()) {
                throw new IllegalStateException("LLM model cost key '" + key + "' must contain at least one alphanumeric character");
            }
        });
        // A configured entry may use any representation of a model name (e.g. the env-var form "gpt54"
        // for "gpt-5.4"), so seed a default only when no configured key strips to the same value -
        // otherwise both would normalize to the same stripped key and collide at lookup-map construction.
        Set<String> configuredStrippedKeys = modelCosts.keySet().stream().map(LLMModelCostConfiguration::stripToAlphanumeric).collect(Collectors.toSet());
        DEFAULT_MODEL_COSTS.forEach((key, value) -> {
            if (!configuredStrippedKeys.contains(stripToAlphanumeric(key))) {
                modelCosts.putIfAbsent(key, value);
            }
        });
    }

    /**
     * Strips every non-alphanumeric character (dashes, dots, etc.) from a model key. Environment-variable
     * configuration cannot represent these characters in keys (e.g. {@code gpt-5.4} becomes {@code GPT54}),
     * so the same stripping is applied to configured keys, seeded defaults, and the runtime model name to
     * make them match.
     *
     * @param value the raw model key or name
     * @return the key with all non-alphanumeric characters removed
     */
    public static String stripToAlphanumeric(String value) {
        return NON_ALPHANUMERIC_PATTERN.matcher(value).replaceAll("");
    }

    private static ModelCostProperties modelCost(float inputCostPerMillionUsd, float outputCostPerMillionUsd, float cachedInputCostPerMillionUsd) {
        ModelCostProperties properties = new ModelCostProperties();
        properties.setInputCostPerMillionUsd(inputCostPerMillionUsd);
        properties.setOutputCostPerMillionUsd(outputCostPerMillionUsd);
        properties.setCachedInputCostPerMillionUsd(cachedInputCostPerMillionUsd);
        return properties;
    }

    public Map<String, ModelCostProperties> getModelCosts() {
        return modelCosts;
    }

    public void setModelCosts(Map<String, ModelCostProperties> modelCosts) {
        this.modelCosts = modelCosts;
    }

    /**
     * Optional USD rates for a single model.
     */
    public static class ModelCostProperties {

        private Float inputCostPerMillionUsd;

        private Float outputCostPerMillionUsd;

        private Float cachedInputCostPerMillionUsd;

        private Float cacheWriteInputCostPerMillionUsd;

        public Float getInputCostPerMillionUsd() {
            return inputCostPerMillionUsd;
        }

        public void setInputCostPerMillionUsd(Float inputCostPerMillionUsd) {
            this.inputCostPerMillionUsd = inputCostPerMillionUsd;
        }

        public Float getOutputCostPerMillionUsd() {
            return outputCostPerMillionUsd;
        }

        public void setOutputCostPerMillionUsd(Float outputCostPerMillionUsd) {
            this.outputCostPerMillionUsd = outputCostPerMillionUsd;
        }

        public Float getCachedInputCostPerMillionUsd() {
            return cachedInputCostPerMillionUsd;
        }

        public void setCachedInputCostPerMillionUsd(Float cachedInputCostPerMillionUsd) {
            this.cachedInputCostPerMillionUsd = cachedInputCostPerMillionUsd;
        }

        public Float getCacheWriteInputCostPerMillionUsd() {
            return cacheWriteInputCostPerMillionUsd;
        }

        public void setCacheWriteInputCostPerMillionUsd(Float cacheWriteInputCostPerMillionUsd) {
            this.cacheWriteInputCostPerMillionUsd = cacheWriteInputCostPerMillionUsd;
        }

        public void setInputCostPerMillionEur(float ignored) {
            // Bind the model entry to suppress seeded USD defaults without storing the legacy rate.
        }

        public void setOutputCostPerMillionEur(float ignored) {
            // Bind the model entry to suppress seeded USD defaults without storing the legacy rate.
        }
    }
}
