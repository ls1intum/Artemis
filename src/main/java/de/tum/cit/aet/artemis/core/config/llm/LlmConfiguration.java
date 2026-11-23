package de.tum.cit.aet.artemis.core.config.llm;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Binds the LLM catalog under {@code artemis.llm} on the nodes that call models, see {@link LlmEnabled}.
 */
@Conditional(LlmEnabled.class)
@Configuration
@Lazy
@EnableConfigurationProperties(LlmProperties.class)
public class LlmConfiguration {
}
