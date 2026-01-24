package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PASSWORD_MIN_LENGTH;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.USERNAME_MAX_LENGTH;
import static de.tum.cit.aet.artemis.core.config.Constants.USERNAME_MIN_LENGTH;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.exception.ConflictingPasskeyConfigurationException;
import de.tum.cit.aet.artemis.core.exception.InvalidAdminConfigurationException;

/**
 * Validates the passkey configuration at application startup.
 * This ensures that invalid configuration combinations are caught early.
 * This bean is marked as non-lazy to ensure validation happens during normal
 * Spring Boot startup, allowing the FailureAnalyzer to provide helpful error messages.
 */
@Component
@Profile(PROFILE_CORE)
@Lazy(false)
public class ConfigurationValidator {

    private static final Logger log = LoggerFactory.getLogger(ConfigurationValidator.class);

    private final Environment environment;

    private final ArtemisConfigHelper artemisConfigHelper;

    private final boolean isPasskeyRequiredForAdministratorFeatures;

    public ConfigurationValidator(Environment environment,
            @Value("${" + Constants.PASSKEY_REQUIRE_FOR_ADMINISTRATOR_FEATURES_PROPERTY_NAME + ":false}") boolean isPasskeyRequiredForAdministratorFeatures) {
        this.environment = environment;
        this.artemisConfigHelper = new ArtemisConfigHelper();
        this.isPasskeyRequiredForAdministratorFeatures = isPasskeyRequiredForAdministratorFeatures;
    }

    /**
     * Validates configurations at startup.
     * Throws appropriate exceptions if configurations are invalid.
     */
    @PostConstruct
    public void validateConfigurations() {
        validatePasskeyConfiguration();
        validateAdminConfiguration();
    }

    /**
     * Validates the passkey configuration at startup.
     * Throws a {@link ConflictingPasskeyConfigurationException} if the configuration is invalid.
     */
    private void validatePasskeyConfiguration() {
        boolean passkeyEnabled = artemisConfigHelper.isPasskeyEnabled(environment);
        boolean passkeyRequiredForAdminFeatures = isPasskeyRequiredForAdministratorFeatures;

        if (passkeyRequiredForAdminFeatures && !passkeyEnabled) {
            String errorMessage = String.format(
                    "Invalid passkey configuration: %s is set to true, but %s is set to false. " + "Passkey must be enabled if it is required for administrator features. "
                            + "Please update your application configuration files to enable passkey or disable the requirement for administrator features.",
                    Constants.PASSKEY_REQUIRE_FOR_ADMINISTRATOR_FEATURES_PROPERTY_NAME, Constants.PASSKEY_ENABLED_PROPERTY_NAME);
            log.error(errorMessage);
            throw new ConflictingPasskeyConfigurationException(errorMessage, Constants.PASSKEY_REQUIRE_FOR_ADMINISTRATOR_FEATURES_PROPERTY_NAME,
                    Constants.PASSKEY_ENABLED_PROPERTY_NAME);
        }

        if (passkeyRequiredForAdminFeatures) {
            log.info("Passkey authentication is required for administrator features");
        }
    }

    /**
     * Validates the internal admin configuration at startup.
     * Throws a {@link InvalidAdminConfigurationException} if the configuration is invalid.
     */
    private void validateAdminConfiguration() {
        Binder binder = Binder.get(environment);

        // Check if internal admin is configured
        var username = binder.bind("artemis.internal-admin.username", String.class).orElse(null);
        var password = binder.bind("artemis.internal-admin.password", String.class).orElse(null);

        if (username != null && password != null) {
            // Validate username length
            if (username.length() < USERNAME_MIN_LENGTH) {
                String errorMessage = String.format("Internal admin username is too short. Minimum length is %d characters, but provided username has %d characters.",
                        USERNAME_MIN_LENGTH, username.length());
                log.error(errorMessage);
                throw new InvalidAdminConfigurationException(errorMessage, "username", "artemis.internal-admin.username", username,
                        String.format("Must be between %d and %d characters", USERNAME_MIN_LENGTH, USERNAME_MAX_LENGTH));
            }

            if (username.length() > USERNAME_MAX_LENGTH) {
                String errorMessage = String.format("Internal admin username is too long. Maximum length is %d characters, but provided username has %d characters.",
                        USERNAME_MAX_LENGTH, username.length());
                log.error(errorMessage);
                throw new InvalidAdminConfigurationException(errorMessage, "username", "artemis.internal-admin.username", username,
                        String.format("Must be between %d and %d characters", USERNAME_MIN_LENGTH, USERNAME_MAX_LENGTH));
            }

            // Validate password length
            if (password.length() < PASSWORD_MIN_LENGTH) {
                String errorMessage = String.format("Internal admin password is too short. Minimum length is %d characters, but provided password has %d characters.",
                        PASSWORD_MIN_LENGTH, password.length());
                log.error(errorMessage);
                throw new InvalidAdminConfigurationException(errorMessage, "password", "artemis.internal-admin.password", "***hidden***",
                        String.format("Must be at least %d characters", PASSWORD_MIN_LENGTH));
            }

            log.info("Internal admin configuration validated successfully");
        }
    }
}
