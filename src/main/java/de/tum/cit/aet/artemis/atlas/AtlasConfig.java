package de.tum.cit.aet.artemis.atlas;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "atlas", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AtlasConfig {
}
