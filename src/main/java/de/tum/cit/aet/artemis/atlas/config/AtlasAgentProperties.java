package de.tum.cit.aet.artemis.atlas.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Chat-agent properties consumed by {@link de.tum.cit.aet.artemis.atlas.service.AtlasAgentDelegationService}.
 * Bound non-strictly because {@code orchestrator} and {@code atlasml} are siblings under the same prefix.
 */
@ConfigurationProperties(prefix = "artemis.atlas")
public record AtlasAgentProperties(@DefaultValue("gpt-5.4-mini") String chatModel, @DefaultValue("0.8") double chatTemperature) {
}
