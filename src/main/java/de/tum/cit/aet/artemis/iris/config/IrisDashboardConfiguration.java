package de.tum.cit.aet.artemis.iris.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration
@Conditional(IrisEnabled.class)
@EnableConfigurationProperties(IrisDashboardProperties.class)
public class IrisDashboardConfiguration {
}
