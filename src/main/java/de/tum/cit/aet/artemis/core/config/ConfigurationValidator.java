package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PASSWORD_MIN_LENGTH;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.USERNAME_MAX_LENGTH;
import static de.tum.cit.aet.artemis.core.config.Constants.USERNAME_MIN_LENGTH;

import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import de.tum.cit.aet.artemis.core.config.weaviate.WeaviateConfigurationProperties;
import de.tum.cit.aet.artemis.core.exception.ConflictingPasskeyConfigurationException;
import de.tum.cit.aet.artemis.core.exception.InvalidAdminConfigurationException;
import de.tum.cit.aet.artemis.core.exception.WeaviateConfigurationException;

/**
 * Validates application configuration at startup.
 * This ensures that invalid configuration combinations are caught early.
 * This bean is marked as non-lazy to ensure validation happens during normal
 * Spring Boot startup, allowing the FailureAnalyzer to provide helpful error messages.
 * <p>
 * Currently validates:
 * <ul>
 * <li>Passkey configuration (conflicting settings)</li>
 * <li>Weaviate configuration (required properties when enabled)</li>
 * </ul>
 */
@Component
@Profile(PROFILE_CORE)
@Lazy(false)
public class ConfigurationValidator {

    private static final Logger log = LoggerFactory.getLogger(ConfigurationValidator.class);

    private static final int MIN_PORT = 1;

    private static final int MAX_PORT = 65535;

    private final Environment environment;

    private final ArtemisConfigHelper artemisConfigHelper;

    private final boolean isPasskeyRequiredForAdministratorFeatures;

    private final String internalAdminUsername;

    private final String internalAdminPassword;

    private final WeaviateConfigurationProperties weaviateProperties;

    public ConfigurationValidator(Environment environment,
            @Value("${" + Constants.PASSKEY_REQUIRE_FOR_ADMINISTRATOR_FEATURES_PROPERTY_NAME + ":false}") boolean isPasskeyRequiredForAdministratorFeatures,
            @Value("${artemis.user-management.internal-admin.username:#{null}}") String internalAdminUsername,
            @Value("${artemis.user-management.internal-admin.password:#{null}}") String internalAdminPassword, WeaviateConfigurationProperties weaviateProperties) {
        this.environment = environment;
        this.artemisConfigHelper = new ArtemisConfigHelper();
        this.isPasskeyRequiredForAdministratorFeatures = isPasskeyRequiredForAdministratorFeatures;

        this.internalAdminUsername = internalAdminUsername;
        this.internalAdminPassword = internalAdminPassword;

        this.weaviateProperties = weaviateProperties;
    }

    /**
     * Validates configurations at startup.
     * Throws appropriate exceptions if configurations are invalid.
     */
    @PostConstruct
    public void validateConfigurations() {
        validatePasskeyConfiguration();
        validateAdminConfiguration();
        validateWeaviateConfiguration();
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
        boolean hasUsername = StringUtils.hasText(internalAdminUsername);
        boolean hasPassword = StringUtils.hasText(internalAdminPassword);

        // Check for partial configuration - both must be provided or neither
        if (hasUsername && !hasPassword) {
            String errorMessage = "Internal admin username is provided but password is missing. Both username and password must be configured together.";
            log.error(errorMessage);
            throw new InvalidAdminConfigurationException(errorMessage, "password", "artemis.user-management.internal-admin.password", "***missing***",
                    "Must be provided when username is configured");
        }

        if (!hasUsername && hasPassword) {
            String errorMessage = "Internal admin password is provided but username is missing. Both username and password must be configured together.";
            log.error(errorMessage);
            throw new InvalidAdminConfigurationException(errorMessage, "username", "artemis.user-management.internal-admin.username", "***missing***",
                    "Must be provided when password is configured");
        }

        // If both are provided, validate their constraints
        if (hasUsername && hasPassword) {
            // Validate username length
            if (internalAdminUsername.length() < USERNAME_MIN_LENGTH) {
                String errorMessage = String.format("Internal admin username is too short. Minimum length is %d characters, but provided username has %d characters.",
                        USERNAME_MIN_LENGTH, internalAdminUsername.length());
                log.error(errorMessage);
                throw new InvalidAdminConfigurationException(errorMessage, "username", "artemis.user-management.internal-admin.username", "***hidden***",
                        String.format("Must be between %d and %d characters", USERNAME_MIN_LENGTH, USERNAME_MAX_LENGTH));
            }

            if (internalAdminUsername.length() > USERNAME_MAX_LENGTH) {
                String errorMessage = String.format("Internal admin username is too long. Maximum length is %d characters, but provided username has %d characters.",
                        USERNAME_MAX_LENGTH, internalAdminUsername.length());
                log.error(errorMessage);
                throw new InvalidAdminConfigurationException(errorMessage, "username", "artemis.user-management.internal-admin.username", "***hidden***",
                        String.format("Must be between %d and %d characters", USERNAME_MIN_LENGTH, USERNAME_MAX_LENGTH));
            }

            // Validate password length
            if (internalAdminPassword.length() < PASSWORD_MIN_LENGTH) {
                String errorMessage = String.format("Internal admin password is too short. Minimum length is %d characters, but provided password has %d characters.",
                        PASSWORD_MIN_LENGTH, internalAdminPassword.length());
                log.error(errorMessage);
                throw new InvalidAdminConfigurationException(errorMessage, "password", "artemis.user-management.internal-admin.password", "***hidden***",
                        String.format("Must be at least %d characters", PASSWORD_MIN_LENGTH));
            }

            log.info("Internal admin configuration validated successfully");
        }
    }

    /**
     * Validates the Weaviate configuration when Weaviate is enabled.
     * Throws a {@link WeaviateConfigurationException} if required properties are missing or invalid.
     */
    private void validateWeaviateConfiguration() {
        if (!weaviateProperties.isEnabled()) {
            return;
        }

        List<String> invalidProperties = new ArrayList<>();

        if (weaviateProperties.getHost() == null || weaviateProperties.getHost().isBlank()) {
            invalidProperties.add("artemis.weaviate.host (must not be empty)");
        }

        if (!isValidPort(weaviateProperties.getPort())) {
            invalidProperties.add("artemis.weaviate.port (must be between " + MIN_PORT + " and " + MAX_PORT + ")");
        }

        if (!isValidPort(weaviateProperties.getGrpcPort())) {
            invalidProperties.add("artemis.weaviate.grpc-port (must be between " + MIN_PORT + " and " + MAX_PORT + ")");
        }

        if (!invalidProperties.isEmpty()) {
            String errorMessage = "Invalid Weaviate configuration: Weaviate is enabled but the following properties are missing or invalid: "
                    + String.join(", ", invalidProperties);
            log.error(errorMessage);
            throw new WeaviateConfigurationException(errorMessage, invalidProperties);
        }

        log.info("Weaviate is enabled and configured with host: {}:{} (gRPC port: {})", weaviateProperties.getHost(), weaviateProperties.getPort(),
                weaviateProperties.getGrpcPort());
    }

    private boolean isValidPort(int port) {
        return port >= MIN_PORT && port <= MAX_PORT;
    }
}
