package de.tum.cit.aet.artemis.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

/**
 * {@code SpringAIAutoConfigurationFilter} is a custom {@link AutoConfigurationImportFilter}
 * that conditionally excludes certain Spring AI Azure/OpenAI auto-configurations from being
 * loaded into the application context.
 * <p>
 * This filter checks the value of the property defined by
 * {@link Constants#HYPERION_ENABLED_PROPERTY_NAME}. If the property is {@code false} or absent,
 * all auto-configuration fully qualified class names starting with org.springframework.ai will be
 * filtered out and not applied. If the property is {@code true}, the filter allows all
 * auto-configurations to proceed (it does not re-include anything excluded elsewhere such
 * as through {@code spring.autoconfigure.exclude} in YAML).
 * <p>
 * This mechanism is useful to prevent unnecessary bean creation when the
 * "Hyperion" feature is disabled, while still permitting the application to start normally.
 * <p>
 * Note that this filter only affects the specified classes. Other exclusions (e.g. via
 * {@code spring.autoconfigure.exclude} in {@code application.yml}) still apply independently.
 *
 * <h3>Registration</h3>
 * To activate this filter, it must be declared in
 * {@code src/main/resources/META-INF/spring.factories}
 *
 *
 */

public class SpringAIAutoConfigurationFilter implements AutoConfigurationImportFilter, EnvironmentAware {

    private static final Logger log = LoggerFactory.getLogger(SpringAIAutoConfigurationFilter.class);

    private Environment env;

    @Override
    public boolean[] match(String[] autoConfigurationClasses, AutoConfigurationMetadata metadata) {
        boolean hyperionEnabled = env.getProperty(Constants.HYPERION_ENABLED_PROPERTY_NAME, Boolean.class, false);

        boolean[] matches = new boolean[autoConfigurationClasses.length];
        for (int i = 0; i < autoConfigurationClasses.length; i++) {
            String fullyQualifiedClassName = autoConfigurationClasses[i];

            // autoConfigurationClasses can contain null values which leads to a NPE in the contains check below, so we handle it here
            if (fullyQualifiedClassName == null) {
                continue;
            }

            String QUIZ_ENABLED_PROPERTY_NAME = "artemis.ai.quiz-generation.enabled"; // TODO: move to Constants file
            // and adapt console log
            boolean quizAiEnabled = env.getProperty(QUIZ_ENABLED_PROPERTY_NAME, Boolean.class, false);
            log.info("SpringAIAutoConfigurationFilter: quizAiEnabled={}, hyperionEnabled={}", quizAiEnabled, hyperionEnabled);
            matches[i] = hyperionEnabled || quizAiEnabled || !fullyQualifiedClassName.startsWith("org.springframework.ai");

            if (!matches[i]) {
                log.debug("Excluding auto-configuration: {} because Hyperion is disabled", fullyQualifiedClassName);
            }
        }
        return matches;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.env = environment;
    }
}
