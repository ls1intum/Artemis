package de.tum.cit.aet.artemis.hyperion.config.worker;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_AIWORKER;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Profile;

/** Explicitly installs Hyperion only when the operator selects its workload. */
@AutoConfiguration
@Profile(PROFILE_AIWORKER)
@ConditionalOnProperty(name = "artemis.aiworker.workload", havingValue = "hyperion-generation")
@ComponentScan(basePackages = { "de.tum.cit.aet.artemis.hyperion.config.worker", "de.tum.cit.aet.artemis.hyperion.service.worker" })
public class HyperionWorkloadConfiguration {
}
