package de.tum.cit.aet.artemis.core.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.SpringApplication;

/**
 * Inlined replacement for {@code tech.jhipster.config.DefaultProfileUtil}.
 * <p>
 * Sets the default Spring profile to {@code dev} when no profile is explicitly configured.
 */
public final class DefaultProfileUtil {

    private static final String SPRING_PROFILE_DEFAULT = "spring.profiles.default";

    private DefaultProfileUtil() {
    }

    public static void addDefaultProfile(SpringApplication app) {
        Map<String, Object> defProperties = new HashMap<>();
        defProperties.put(SPRING_PROFILE_DEFAULT, ArtemisConstants.SPRING_PROFILE_DEVELOPMENT);
        app.setDefaultProperties(defProperties);
    }
}
