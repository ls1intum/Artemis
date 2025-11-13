package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Validates the passkey configuration at application startup.
 * This ensures that invalid configuration combinations are caught early.
 * This bean is marked as non-lazy to ensure validation happens during normal
 * Spring Boot startup, allowing the FailureAnalyzer to provide helpful error messages.
 */
@Component
@Profile(PROFILE_CORE)
@Lazy(false)
public class PasskeyConfigurationValidator {

    private static final Logger log = LoggerFactory.getLogger(PasskeyConfigurationValidator.class);

    private final Environment environment;

    private final ArtemisConfigHelper artemisConfigHelper;

    public PasskeyConfigurationValidator(Environment environment) {
        this.environment = environment;
        this.artemisConfigHelper = new ArtemisConfigHelper();
    }

    /**
     * Validates the passkey configuration at startup.
     * Throws a PasskeyConfigurationException if the configuration is invalid.
     */
    @PostConstruct
    public void validatePasskeyConfiguration() {
        boolean passkeyEnabled = artemisConfigHelper.isPasskeyEnabled(environment);
        boolean passkeyRequiredForAdminFeatures = artemisConfigHelper.isPasskeyRequiredForAdministratorFeatures(environment);

        if (passkeyRequiredForAdminFeatures && !passkeyEnabled) {
            String errorMessage = String.format(
                    "Invalid passkey configuration: %s is set to true, but %s is set to false. " + "Passkey must be enabled if it is required for administrator features. "
                            + "Please update your application configuration files to enable passkey or disable the requirement for administrator features.",
                    Constants.PASSKEY_REQUIRE_FOR_ADMINISTRATOR_FEATURES_PROPERTY_NAME, Constants.PASSKEY_ENABLED_PROPERTY_NAME);
            log.error(errorMessage);
            throw new PasskeyConfigurationException(errorMessage, Constants.PASSKEY_REQUIRE_FOR_ADMINISTRATOR_FEATURES_PROPERTY_NAME, Constants.PASSKEY_ENABLED_PROPERTY_NAME);
        }

        if (passkeyRequiredForAdminFeatures) {
            log.info("Passkey authentication is required for administrator features");
        }
    }
}
