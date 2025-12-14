package de.tum.cit.aet.artemis.core.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Configuration holder for LLM cost estimates in EUR (per million tokens) pulled from configuration.
 */
@Component
@Lazy
@ConfigurationProperties(prefix = "spring.ai.llm")
@ConditionalOnProperty(name = Constants.HYPERION_ENABLED_PROPERTY_NAME, havingValue = "true", matchIfMissing = false)
public class LlmUsageProperties {

    /**
     * Populated via {@code spring.ai.llm.costs} (application-artemis.yml).
     * Defaults to empty so missing entries fall back to zero cost at usage time.
     */
    private Map<String, ModelCost> costs = new HashMap<>();

    public Map<String, ModelCost> getCosts() {
        return costs;
    }

    public void setCosts(Map<String, ModelCost> costs) {
        this.costs = costs;
    }

    public record ModelCost(float costPerMillionInput, float costPerMillionOutput) {
    }
}
