package de.tum.cit.aet.artemis.aiworker.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.annotation.Profile;

/** Starts the isolated AI Worker from the same Artemis distribution without scanning server services. */
@SpringBootApplication(scanBasePackages = { "de.tum.cit.aet.artemis.aiworker", "de.tum.cit.aet.artemis.hyperion.config.worker" })
@Profile("aiworker")
public class AiWorkerApplication {

    /**
     * Starts a separate AI Worker process from the Artemis WAR.
     *
     * @param args Spring Boot arguments
     */
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(AiWorkerApplication.class);
        application.setAdditionalProfiles("aiworker", "aiworker-standalone");
        application.setWebApplicationType(WebApplicationType.NONE);
        application.addListeners((ApplicationEnvironmentPreparedEvent event) -> {
            if (event.getEnvironment().matchesProfiles("core | buildagent | localci | localvc")) {
                throw new IllegalArgumentException("The standalone AI Worker cannot use Artemis server or build-agent profiles");
            }
        });
        application.run(args);
    }
}
