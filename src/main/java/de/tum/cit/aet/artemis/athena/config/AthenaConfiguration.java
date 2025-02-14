package de.tum.cit.aet.artemis.athena.config;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import de.tum.cit.aet.artemis.core.config.conditions.AthenaCondition;

@Configuration
@Conditional(AthenaCondition.class)
public class AthenaConfiguration {
}
