package de.tum.cit.aet.artemis.buildagent;

import static de.tum.cit.aet.artemis.core.config.Constants.LOCAL;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_AIWORKER;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import de.tum.cit.aet.artemis.core.config.BuildAgentGitPasswordValidator;
import de.tum.cit.aet.artemis.core.config.DistributedDataProviderResolver;
import de.tum.cit.aet.artemis.core.config.ProgrammingLanguageConfiguration;
import de.tum.cit.aet.artemis.core.config.WorkerProviderConfiguration;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.localci.service.DistributedDataAccessService;

/** Standalone LocalCI agent with its own runtime classpath and health endpoint. */
@SpringBootApplication
@Import({ BuildAgentGitPasswordValidator.class, WorkerProviderConfiguration.class, DistributedDataAccessService.class, TempFileUtilService.class })
@EnableConfigurationProperties(ProgrammingLanguageConfiguration.class)
@EnableScheduling
@EnableAsync
public class BuildAgentApplication {

    /**
     * Start the LocalCI agent and connect to the configured cluster.
     *
     * @param args Spring Boot command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(BuildAgentApplication.class);
        application.setAdditionalProfiles(PROFILE_BUILDAGENT);
        application.addInitializers(context -> {
            var environment = context.getEnvironment();
            if (environment.matchesProfiles(PROFILE_CORE, PROFILE_AIWORKER)) {
                throw new IllegalStateException("The dedicated build agent cannot run the core or aiworker profile.");
            }
            if (DistributedDataProviderResolver.isProvider(environment, LOCAL)) {
                throw new IllegalStateException("The dedicated build agent requires a Redis or Hazelcast connection to core.");
            }
        });
        application.run(args);
    }
}
