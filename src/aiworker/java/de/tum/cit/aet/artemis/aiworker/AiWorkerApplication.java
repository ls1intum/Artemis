package de.tum.cit.aet.artemis.aiworker;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_AIWORKER;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.annotation.Import;

import de.tum.cit.aet.artemis.core.config.AiWorkerProfileValidation;
import de.tum.cit.aet.artemis.core.config.WorkerProviderConfiguration;

/** Headless supervisor with the shipped Hyperion generation workload. */
@SpringBootApplication(scanBasePackages = { "de.tum.cit.aet.artemis.aiworker", "de.tum.cit.aet.artemis.hyperion.service.worker", "de.tum.cit.aet.artemis.core.config.llm",
        "de.tum.cit.aet.artemis.core.service.llm" })
@Import(WorkerProviderConfiguration.class)
public class AiWorkerApplication {

    /**
     * Start the headless generation worker.
     *
     * @param args Spring Boot command-line arguments
     */
    public static void main(String[] args) {
        var app = new SpringApplication(AiWorkerApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.addListeners((ApplicationEnvironmentPreparedEvent event) -> {
            var environment = event.getEnvironment();
            if (!environment.matchesProfiles(PROFILE_AIWORKER)) {
                throw new IllegalArgumentException("The AI Worker executable requires the aiworker profile");
            }
            AiWorkerProfileValidation.validate(environment);
        });
        app.run(args);
    }
}
