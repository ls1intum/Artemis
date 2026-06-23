package de.tum.cit.aet.artemis.atlas.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Conditional(AtlasEnabled.class)
@Lazy
@Configuration
@EnableConfigurationProperties({ AtlasAgentProperties.class, AtlasOrchestratorProperties.class })
public class AtlasOrchestratorConfiguration {
}
