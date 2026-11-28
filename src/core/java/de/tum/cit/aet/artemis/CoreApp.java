package de.tum.cit.aet.artemis;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_AIWORKER;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;

/** Entry point for the dedicated core distribution. */
public final class CoreApp {

    private CoreApp() {
    }

    /**
     * Start a core node with the installed cluster profiles.
     *
     * @param args Spring Boot command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(ArtemisApp.class);
        application.setAdditionalProfiles(PROFILE_CORE);
        application.addListeners((ApplicationEnvironmentPreparedEvent event) -> {
            if (event.getEnvironment().matchesProfiles(PROFILE_BUILDAGENT, PROFILE_AIWORKER)) {
                throw new IllegalArgumentException("The core WAR cannot run the buildagent or aiworker profile");
            }
        });
        ArtemisApp.run(application, args);
    }
}
