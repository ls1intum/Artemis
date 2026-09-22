package de.tum.cit.aet.artemis.hyperion.config.worker;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;

/** Explicitly installs Hyperion only when the operator selects its workload. */
@AutoConfiguration
@ConditionalOnProperty(name = "artemis.aiworker.workload", havingValue = "hyperion-generation")
@ComponentScan(basePackages = { "de.tum.cit.aet.artemis.hyperion.config.worker", "de.tum.cit.aet.artemis.hyperion.service.worker" })
public class HyperionWorkloadConfiguration {
}
