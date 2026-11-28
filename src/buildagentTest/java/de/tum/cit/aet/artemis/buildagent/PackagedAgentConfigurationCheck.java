package de.tum.cit.aet.artemis.buildagent;

import java.util.Map;

import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.StandardEnvironment;

/** Loads Spring profile defaults directly from the packaged build agent, without contacting cluster services. */
public final class PackagedAgentConfigurationCheck {

    private PackagedAgentConfigurationCheck() {
    }

    public static void main(String[] args) {
        checkProfileDefaults(new String[] { "prod", "buildagent" }, Map.of("spring.hazelcast.localInstances", "false", "artemis.continuous-integration.build-runner", "docker",
                "spring.threads.virtual.enabled", "true", "spring.task.scheduling.pool.size", "2"));
        checkProfileDefaults(new String[] { "prod", "buildagent", "k8s" }, Map.of("spring.hazelcast.localInstances", "false", "artemis.continuous-integration.build-runner",
                "kubernetes", "artemis.continuous-integration.kubernetes.workload-service-account", "artemis-localci-workload"));
    }

    private static void checkProfileDefaults(String[] profiles, Map<String, String> expected) {
        var environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.setActiveProfiles(profiles);
        ConfigDataEnvironmentPostProcessor.applyTo(environment);
        expected.forEach((key, value) -> {
            String actual = environment.getProperty(key);
            if (!value.equals(actual)) {
                throw new IllegalStateException(String.join(",", profiles) + ": expected " + key + "=" + value + ", got " + actual);
            }
        });
    }
}
