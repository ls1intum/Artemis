package de.tum.cit.aet.artemis.atlas.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AtlasOrchestratorProperties.class)
public class AtlasOrchestratorConfiguration {
}
