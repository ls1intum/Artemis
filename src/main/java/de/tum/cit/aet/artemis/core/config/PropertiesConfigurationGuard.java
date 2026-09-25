package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Lazy(false)
@Profile(PROFILE_CORE + " | " + PROFILE_BUILDAGENT)
public class PropertiesConfigurationGuard implements InitializingBean {

    /**
     * Template values, compared case-insensitively. Besides generic placeholders, this lists the defaults that Artemis configuration files, the Helm chart
     * ({@code Example University}, {@code Max Mustermann}) and the Ansible collection ({@code Anonymous University}, {@code Anonymous University Admin}) have
     * shipped, so an installation that kept one of them is asked for its own value.
     */
    private static final Set<String> PLACEHOLDERS = Set.of("admin", "some artemis operator", "some universities admin", "your university", "your name", "your operator",
            "university name", "operator name", "admin name", "todo", "tbd", "changeme", "example university", "max mustermann", "anonymous university",
            "anonymous university admin", "example university it services");

    @Value("${info.operatorAdminName:#{null}}")
    private String operatorAdminName;

    @Value("${info.universityName:#{null}}")
    private String universityName;

    @Value("${info.operatorName:#{null}}")
    private String operatorName;

    /** Rejects incomplete installation metadata on every core and build-agent node before readiness. */
    @Override
    public void afterPropertiesSet() {
        List<String> invalid = new ArrayList<>();
        if (isInvalid(operatorName)) {
            invalid.add("info.operatorName (INFO_OPERATORNAME)");
        }
        if (isInvalid(operatorAdminName)) {
            invalid.add("info.operatorAdminName (INFO_OPERATORADMINNAME)");
        }
        if (isInvalid(universityName)) {
            invalid.add("info.universityName (INFO_UNIVERSITYNAME)");
        }
        if (!invalid.isEmpty()) {
            throw new IllegalArgumentException("Configure meaningful values for " + String.join(", ", invalid)
                    + "; these installation properties are required on every server start, independently of telemetry settings, and displayed on the About page.");
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
