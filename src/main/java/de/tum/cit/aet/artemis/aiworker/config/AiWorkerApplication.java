package de.tum.cit.aet.artemis.aiworker.config;

import static de.tum.cit.aet.artemis.core.config.Constants.HAZELCAST;
import static de.tum.cit.aet.artemis.core.config.Constants.LOCAL;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_AIWORKER;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.annotation.Profile;

import de.tum.cit.aet.artemis.core.config.DistributedDataProviderResolver;

/** Starts the isolated AI Worker from the same Artemis distribution without scanning server services. */
@SpringBootApplication(scanBasePackages = "de.tum.cit.aet.artemis.aiworker")
@Profile(PROFILE_AIWORKER)
public class AiWorkerApplication {

    /**
     * Starts a separate AI Worker process from the Artemis WAR.
     *
     * @param args Spring Boot arguments
     */
    public static void main(String[] args) {
        standaloneApplication().run(args);
    }

    static SpringApplication standaloneApplication() {
        SpringApplication application = new SpringApplication(AiWorkerApplication.class);
        application.setAdditionalProfiles(PROFILE_AIWORKER, "aiworker-standalone");
        application.setWebApplicationType(WebApplicationType.NONE);
        application.addListeners((ApplicationEnvironmentPreparedEvent event) -> {
            if (event.getEnvironment().getProperty("spring.main.web-application-type", WebApplicationType.class, WebApplicationType.NONE) != WebApplicationType.NONE) {
                throw new IllegalArgumentException("The standalone AI Worker cannot enable an HTTP server");
            }
            if (event.getEnvironment().matchesProfiles("core | buildagent | localci | localvc")) {
                throw new IllegalArgumentException("The standalone AI Worker cannot use Artemis server or build-agent profiles");
            }
            if (DistributedDataProviderResolver.isProvider(event.getEnvironment(), LOCAL)) {
                throw new IllegalArgumentException("The standalone AI Worker requires a shared Hazelcast or Redis data provider");
            }
            if (DistributedDataProviderResolver.isProvider(event.getEnvironment(), HAZELCAST)
                    && !event.getEnvironment().getProperty("eureka.client.enabled", Boolean.class, false)) {
                throw new IllegalArgumentException("The standalone AI Worker requires Eureka discovery with Hazelcast");
            }
        });
        return application;
    }
}
