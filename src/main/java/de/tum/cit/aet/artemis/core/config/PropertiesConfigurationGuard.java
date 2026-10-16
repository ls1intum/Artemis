package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Refuses to start a production core node without meaningful installation metadata, which the About page shows and the scheduling node reports as telemetry.
 * Development servers and test servers ({@code info.testServer: true}) send no telemetry and may leave it out, and build agents neither show nor report it, so
 * none of them is checked.
 */
@Component
@Lazy(false)
@Profile(PROFILE_CORE)
public class PropertiesConfigurationGuard implements InitializingBean {

    /**
     * Template values, compared case-insensitively. Besides generic placeholders, this lists the defaults that Artemis configuration files, the Helm chart
     * ({@code Example University}, {@code Max Mustermann}) and the Ansible collection ({@code Anonymous University}, {@code Anonymous University Admin}) have
     * shipped, so an installation that kept one of them is asked for its own value.
     */
    private static final Set<String> PLACEHOLDERS = Set.of("admin", "some artemis operator", "some universities admin", "your university", "your name", "your operator",
            "university name", "operator name", "admin name", "todo", "tbd", "changeme", "example university", "max mustermann", "anonymous university",
            "anonymous university admin", "example university it services", "some artemis dev", "n/a", "none", "unknown");

    private final Environment environment;

    public PropertiesConfigurationGuard(Environment environment) {
        this.environment = environment;
    }

    /** Rejects incomplete installation metadata on a production core node before readiness. */
    @Override
    public void afterPropertiesSet() {
        if (!environment.matchesProfiles(ArtemisConstants.SPRING_PROFILE_PRODUCTION) || environment.getProperty("info.testServer", Boolean.class, false)) {
            return;
        }
        List<String> invalid = new ArrayList<>();
        if (isInvalid(environment.getProperty("info.operatorName"))) {
            invalid.add("info.operatorName (INFO_OPERATORNAME)");
        }
        if (isInvalid(environment.getProperty("info.operatorAdminName"))) {
            invalid.add("info.operatorAdminName (INFO_OPERATORADMINNAME)");
        }
        if (isInvalid(environment.getProperty("info.universityName"))) {
            invalid.add("info.universityName (INFO_UNIVERSITYNAME)");
        }
        if (!invalid.isEmpty()) {
            throw new IllegalArgumentException("Configure meaningful values for " + String.join(", ", invalid)
                    + "; these installation properties are required on every production core node, independently of telemetry settings, and displayed on the About page."
                    + " Test servers (info.testServer: true) and development servers do not need them.");
        }
    }

    private static boolean isInvalid(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        return PLACEHOLDERS.contains(normalized) || (normalized.startsWith("<") && normalized.endsWith(">"));
    }
}
