package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE_AND_SCHEDULING;

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
@Profile(PROFILE_CORE_AND_SCHEDULING)
public class PropertiesConfigurationGuard implements InitializingBean {

    private static final Set<String> PLACEHOLDERS = Set.of("admin", "some artemis operator", "some universities admin", "your university", "your name", "your operator",
            "university name", "operator name", "admin name", "todo", "tbd", "changeme");

    @Value("${artemis.telemetry.enabled:false}")
    private boolean telemetryEnabled;

    @Value("${info.operatorAdminName:#{null}}")
    private String operatorAdminName;

    @Value("${info.universityName:#{null}}")
    private String universityName;

    @Value("${info.operatorName:#{null}}")
    private String operatorName;

    /** Rejects incomplete telemetry metadata before the application becomes ready. */
    @Override
    public void afterPropertiesSet() {
        List<String> invalid = new ArrayList<>();
        if (telemetryEnabled) {
            if (isInvalid(operatorName)) {
                invalid.add("info.operatorName");
            }
            if (isInvalid(operatorAdminName)) {
                invalid.add("info.operatorAdminName");
            }
            if (isInvalid(universityName)) {
                invalid.add("info.universityName");
            }
        }
        else if (operatorName == null || operatorName.isBlank()) {
            invalid.add("info.operatorName");
        }
        if (!invalid.isEmpty()) {
            throw new IllegalArgumentException("Configure meaningful values for " + String.join(", ", invalid)
                    + (telemetryEnabled ? "; these properties are required when artemis.telemetry.enabled=true, even when sendAdminDetails=false."
                            : "; the operator is required for the about page."));
        }
    }

    private static boolean isInvalid(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        return PLACEHOLDERS.contains(normalized) || normalized.startsWith("<") && normalized.endsWith(">");
    }
}
