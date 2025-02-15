package de.tum.cit.aet.artemis.core.config.runtime_property;

import static de.tum.cit.aet.artemis.core.config.conditions.ArtemisConfigHelper.isBuildAgentOnlyMode;
import static de.tum.cit.aet.artemis.core.config.conditions.ArtemisConfigHelper.isGitLabCIEnabled;
import static de.tum.cit.aet.artemis.core.config.conditions.ArtemisConfigHelper.isGitLabEnabled;
import static de.tum.cit.aet.artemis.core.config.conditions.ArtemisConfigHelper.isJenkinsEnabled;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.core.env.ConfigurableEnvironment;

public class ConfigValidationPostProcessor {

    /**
     * Attach flexible validation logic to the Spring Boot application before performing
     * dependency injection by listening on the {@link ApplicationEnvironmentPreparedEvent}.
     *
     * @param app the Spring Boot application to perform the validation on
     */
    public void attachTo(SpringApplication app) {
        app.addListeners((ApplicationEnvironmentPreparedEvent event) -> {
            ConfigurableEnvironment environment = event.getEnvironment();
            validateConfig(environment);
        });
    }

    /**
     * Custom logic to validate configurations.
     * For instance, running BUILDAGENT on a node with a dedicated CI like Jenkins or GitLabCI enabled make little sense.
     */
    private void validateConfig(ConfigurableEnvironment environment) {
        if (isBuildAgentOnlyMode(environment)) {
            if (isGitLabEnabled(environment) || isJenkinsEnabled(environment)) {
                throw new IllegalStateException("The build agent only mode is not allowed with the gitlab or jenkins profile.");
            }
        }

        if (isJenkinsEnabled(environment) && isGitLabCIEnabled(environment)) {
            throw new IllegalStateException("The jenkins and gitlab profiles cannot be active at the same time.");
        }
        // further checks can be added here
    }
}
