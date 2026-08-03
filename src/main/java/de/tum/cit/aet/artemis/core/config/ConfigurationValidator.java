package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PASSWORD_MIN_LENGTH;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.USERNAME_MAX_LENGTH;
import static de.tum.cit.aet.artemis.core.config.Constants.USERNAME_MIN_LENGTH;
import static de.tum.cit.aet.artemis.globalsearch.config.SupportedVectorizer.TEXT2VEC_OPENAI;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import de.tum.cit.aet.artemis.core.exception.ConflictingPasskeyConfigurationException;
import de.tum.cit.aet.artemis.core.exception.InsecureDefaultCredentialException;
import de.tum.cit.aet.artemis.core.exception.InvalidAdminConfigurationException;
import de.tum.cit.aet.artemis.globalsearch.config.SupportedVectorizer;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateConfigurationProperties;
import de.tum.cit.aet.artemis.globalsearch.exception.WeaviateConfigurationException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.DecodingException;

/**
 * Validates application configuration at startup.
 * This ensures that invalid configuration combinations are caught early.
 * This bean is marked as non-lazy to ensure validation happens during normal
 * Spring Boot startup, allowing the FailureAnalyzer to provide helpful error messages.
 * <p>
 * Currently validates:
 * <ul>
 * <li>Passkey configuration (conflicting settings)</li>
 * <li>Internal admin credentials (username/password present together and within length bounds)</li>
 * <li>Security-critical properties that still hold a shipped example value ({@code prod} profile only)</li>
 * <li>Weaviate configuration (required properties when enabled)</li>
 * </ul>
 */
@Component
@Profile(PROFILE_CORE)
@Lazy(false)
public class ConfigurationValidator {

    private static final Logger log = LoggerFactory.getLogger(ConfigurationValidator.class);

    public static final int MIN_PORT = 1;

    public static final int MAX_PORT = 65535;

    public static final String HTTP_SCHEME = "http";

    public static final String HTTPS_SCHEME = "https";

    private static final String JWT_SECRET_PROPERTY = "jhipster.security.authentication.jwt.secret";

    private static final String JWT_BASE64_SECRET_PROPERTY = "jhipster.security.authentication.jwt.base64-secret";

    private static final String INTERNAL_ADMIN_PASSWORD_PROPERTY = "artemis.user-management.internal-admin.password";

    private static final String BUILD_AGENT_GIT_PASSWORD_PROPERTY = "artemis.version-control.build-agent-git-password";

    /** HS512 requires a 512-bit key; {@code Keys.hmacShaKeyFor} rejects anything shorter. */
    private static final int MIN_JWT_SECRET_LENGTH_IN_BYTES = 64;

    /**
     * JWT signing keys that Artemis has shipped as examples. Both the Base64 form found in the packaged
     * configuration and its decoded plaintext are listed, because
     * {@link de.tum.cit.aet.artemis.core.security.jwt.TokenProvider#init()} accepts either property.
     * <p>
     * Entries must never be removed: an operator who once copied a value needs it to keep failing.
     */
    private static final Set<String> KNOWN_DEFAULT_JWT_SECRETS = Set.of("bXktc2VjcmV0LWtleS13aGljaC1zaG91bGQtYmUtY2hhbmdlZC1pbi1wcm9kdWN0aW9uLWFuZC1iZS1iYXNlNjQtZW5jb2RlZAo=",
            "my-secret-key-which-should-be-changed-in-production-and-be-base64-encoded\n", "my-secret-key-which-should-be-changed-in-production-and-be-base64-encoded");

    /**
     * Internal-admin passwords that Artemis has shipped as examples: {@code artemis_admin} from
     * {@code config/application-artemis.yml}, {@code artemis-admin} from the production-setup security documentation.
     * <p>
     * Entries must never be removed, for the same reason as in {@link #KNOWN_DEFAULT_JWT_SECRETS}.
     */
    private static final Set<String> KNOWN_DEFAULT_ADMIN_PASSWORDS = Set.of("artemis_admin", "artemis-admin");

    /**
     * Build-agent git passwords that Artemis has shipped as examples: {@code buildjob_password} from
     * {@code config/application-localvc.yml} and {@code config/application-buildagent.yml},
     * {@code buildagent_password} from the production-setup security documentation, and {@code artemis_admin} from the
     * Jenkins LocalVC setup, which reuses the internal-admin password here.
     * <p>
     * Entries must never be removed, for the same reason as in {@link #KNOWN_DEFAULT_JWT_SECRETS}.
     */
    private static final Set<String> KNOWN_DEFAULT_BUILD_AGENT_GIT_PASSWORDS = Set.of("buildjob_password", "buildagent_password", "artemis_admin");

    private final Environment environment;

    private final ArtemisConfigHelper artemisConfigHelper;

    private final boolean isPasskeyRequiredForAdministratorFeatures;

    private final String internalAdminUsername;

    private final String internalAdminPassword;

    private final boolean weaviateEnabled;

    private final String weaviateHost;

    private final int weaviatePort;

    private final int weaviateGrpcPort;

    private final String weaviateScheme;

    private final String weaviateVectorizerModule;

    private final String weaviateOpenAiBaseUrl;

    private final String weaviateGpuApiKey;

    private final String serverUrl;

    private final boolean isOpenApiDocsGeneration;

    public ConfigurationValidator(Environment environment,
            @Value("${" + Constants.PASSKEY_REQUIRE_FOR_ADMINISTRATOR_FEATURES_PROPERTY_NAME + ":false}") boolean isPasskeyRequiredForAdministratorFeatures,
            @Value("${artemis.user-management.internal-admin.username:#{null}}") String internalAdminUsername,
            @Value("${artemis.user-management.internal-admin.password:#{null}}") String internalAdminPassword, @Value("${artemis.weaviate.enabled:false}") boolean weaviateEnabled,
            @Value("${artemis.weaviate.http-host:#{null}}") String weaviateHost,
            @Value("${artemis.weaviate.http-port:" + WeaviateConfigurationProperties.DEFAULT_HTTP_PORT + "}") int weaviatePort,
            @Value("${artemis.weaviate.grpc-port:" + WeaviateConfigurationProperties.DEFAULT_GRPC_PORT + "}") int weaviateGrpcPort,
            @Value("${artemis.weaviate.scheme:#{null}}") String weaviateScheme, @Value("${artemis.weaviate.vectorizer-module:#{null}}") String weaviateVectorizerModule,
            @Value("${artemis.weaviate.open-ai-base-url:#{null}}") String weaviateOpenAiBaseUrl, @Value("${artemis.weaviate.gpu-api-key:#{null}}") String weaviateGpuApiKey,
            @Value("${artemis.openapi-docs-generation:false}") boolean isOpenApiDocsGeneration, @Value("${server.url:}") String serverUrl) {
        this.environment = environment;
        this.artemisConfigHelper = new ArtemisConfigHelper();
        this.isPasskeyRequiredForAdministratorFeatures = isPasskeyRequiredForAdministratorFeatures;

        this.internalAdminUsername = internalAdminUsername;
        this.internalAdminPassword = internalAdminPassword;

        this.weaviateEnabled = weaviateEnabled;
        this.weaviateHost = weaviateHost;
        this.weaviatePort = weaviatePort;
        this.weaviateGrpcPort = weaviateGrpcPort;
        this.weaviateScheme = weaviateScheme;
        this.weaviateVectorizerModule = weaviateVectorizerModule;
        this.weaviateOpenAiBaseUrl = weaviateOpenAiBaseUrl;
        this.weaviateGpuApiKey = weaviateGpuApiKey;
        this.isOpenApiDocsGeneration = isOpenApiDocsGeneration;
        this.serverUrl = serverUrl;
    }

    /**
     * Validates configurations at startup.
     * Throws appropriate exceptions if configurations are invalid.
     */
    @PostConstruct
    public void validateConfigurations() {
        validateServerUrl();
        validatePasskeyConfiguration();
        validateAdminConfiguration();
        validateNoShippedDefaultCredentials();
        validateWeaviateConfiguration();
    }

    /**
     * Rejects security-critical properties that still hold a value Artemis ships as an example, but only
     * under the {@code prod} profile so local development, tests and CI keep working with the packaged
     * defaults.
     * <p>
     * Every value checked here is published in the Artemis repository. A known JWT signing key lets anyone
     * forge a token for any user with any authority; a known internal-admin password or build-agent git
     * password grants direct access. An <em>absent</em> JWT secret already fails the boot, so a committed
     * default is strictly worse than no configuration at all: it turns a loud failure into a silent one.
     * <p>
     * This deliberately throws rather than warning. A warning in a startup log is routinely missed, and the
     * entire purpose of the check is that the unsafe state must not reach a running production system.
     */
    private void validateNoShippedDefaultCredentials() {
        if (!environment.matchesProfiles(ArtemisConstants.SPRING_PROFILE_PRODUCTION)) {
            return;
        }

        validateJwtSecret();
        validateProductionInternalAdminPassword();
        validateBuildAgentGitPassword();

        log.info("Production credential validation passed: no shipped example values are in use");
    }

    /**
     * Validates the JWT signing key. {@link de.tum.cit.aet.artemis.core.security.jwt.TokenProvider#init()}
     * prefers the plain {@code secret} property over {@code base64-secret} when both are set, so both are
     * checked, and the effective one is length-checked after decoding.
     */
    private void validateJwtSecret() {
        String plainSecret = environment.getProperty(JWT_SECRET_PROPERTY);
        String base64Secret = environment.getProperty(JWT_BASE64_SECRET_PROPERTY);

        // TokenProvider treats a non-empty plain secret as authoritative and uses its raw bytes.
        boolean usesPlainSecret = StringUtils.hasLength(plainSecret);
        String effectiveProperty = usesPlainSecret ? JWT_SECRET_PROPERTY : JWT_BASE64_SECRET_PROPERTY;
        byte[] keyBytes;

        if (usesPlainSecret) {
            rejectIfKnownDefault(plainSecret, KNOWN_DEFAULT_JWT_SECRETS, effectiveProperty,
                    "the configured signing key is a value published in the Artemis repository, so anyone can forge a token for any user with any authority",
                    "Generate a fresh key, e.g. `openssl rand -base64 64`, and prefer the base64-secret property over the plain secret property.");
            keyBytes = plainSecret.getBytes(StandardCharsets.UTF_8);
        }
        else {
            if (!StringUtils.hasLength(base64Secret)) {
                // Left to TokenProvider.init(), which fails on a null/blank base64 value; reported here for a clearer message.
                throw new InsecureDefaultCredentialException(JWT_BASE64_SECRET_PROPERTY, "no JWT signing key is configured",
                        "Generate a key with `openssl rand -base64 64` and supply it as the base64-secret property.");
            }
            rejectIfKnownDefault(base64Secret, KNOWN_DEFAULT_JWT_SECRETS, effectiveProperty,
                    "the configured signing key is a value published in the Artemis repository, so anyone can forge a token for any user with any authority",
                    "Generate a fresh key, e.g. `openssl rand -base64 64`.");
            try {
                keyBytes = Decoders.BASE64.decode(base64Secret);
            }
            catch (DecodingException e) {
                throw new InsecureDefaultCredentialException(JWT_BASE64_SECRET_PROPERTY, "the value is not valid Base64 and cannot be decoded into a signing key",
                        "Generate a key with `openssl rand -base64 64`.");
            }
        }

        // HS512 needs a 512-bit key; Keys.hmacShaKeyFor would reject a shorter one, but with a stack trace rather than guidance.
        if (keyBytes.length < MIN_JWT_SECRET_LENGTH_IN_BYTES) {
            throw new InsecureDefaultCredentialException(effectiveProperty,
                    "the signing key is only %d bytes; HS512 requires at least %d".formatted(keyBytes.length, MIN_JWT_SECRET_LENGTH_IN_BYTES),
                    "Generate a longer key with `openssl rand -base64 64`.");
        }
    }

    /**
     * Rejects an internal-admin password that is a shipped example, or that equals the username. Length is
     * already validated in {@link #validateAdminConfiguration()} for every profile; this adds the
     * production-only content checks.
     */
    private void validateProductionInternalAdminPassword() {
        if (!StringUtils.hasText(internalAdminPassword)) {
            return;
        }

        rejectIfKnownDefault(internalAdminPassword, KNOWN_DEFAULT_ADMIN_PASSWORDS, INTERNAL_ADMIN_PASSWORD_PROPERTY,
                "the internal admin password is a value published in the Artemis repository, and this account is granted SUPER_ADMIN on every startup",
                "Choose a unique password, or leave both internal-admin properties empty to skip creating the account entirely.");

        if (internalAdminUsername != null && constantTimeEquals(internalAdminPassword, internalAdminUsername)) {
            throw new InsecureDefaultCredentialException(INTERNAL_ADMIN_PASSWORD_PROPERTY, "the internal admin password is identical to the internal admin username",
                    "Choose a password unrelated to the username, or leave both internal-admin properties empty to skip creating the account entirely.");
        }
    }

    /**
     * Rejects a shipped example build-agent git password. Matching credentials let a caller read every
     * repository in the installation: {@code LocalVCServletService} returns early on a match, ahead of the
     * rate limit, the repository authorization checks and the VCS access log.
     */
    private void validateBuildAgentGitPassword() {
        String buildAgentGitPassword = environment.getProperty(BUILD_AGENT_GIT_PASSWORD_PROPERTY);
        if (!StringUtils.hasText(buildAgentGitPassword)) {
            return;
        }

        rejectIfKnownDefault(buildAgentGitPassword, KNOWN_DEFAULT_BUILD_AGENT_GIT_PASSWORDS, BUILD_AGENT_GIT_PASSWORD_PROPERTY,
                "the build-agent git password is a value published in the Artemis repository, and a caller presenting it can read every repository "
                        + "without any authorization check or access-log entry",
                "Choose a unique password, and keep it in sync with the build agents' configuration.");
    }

    /**
     * Throws if {@code configuredValue} matches any known shipped default.
     *
     * @param configuredValue the value read from the environment
     * @param knownDefaults   the shipped example values to reject
     * @param propertyPath    the configuration path, used in the error message
     * @param reason          operator-facing explanation; must not contain the value
     * @param remediation     concrete instructions for choosing an acceptable value
     */
    private static void rejectIfKnownDefault(String configuredValue, Set<String> knownDefaults, String propertyPath, String reason, String remediation) {
        // Non-short-circuiting on purpose, so that every candidate is compared regardless of where the match sits.
        boolean matchesDefault = false;
        for (String knownDefault : knownDefaults) {
            matchesDefault |= constantTimeEquals(configuredValue, knownDefault);
        }
        if (matchesDefault) {
            throw new InsecureDefaultCredentialException(propertyPath, reason, remediation);
        }
    }

    /**
     * Compares two secrets without leaking their relationship through timing. Startup is not a realistic
     * timing-attack surface, but comparing secrets this way costs nothing and keeps the intent explicit.
     *
     * @param first  the first value
     * @param second the second value
     * @return whether the two values are equal
     */
    private static boolean constantTimeEquals(String first, String second) {
        return MessageDigest.isEqual(first.getBytes(StandardCharsets.UTF_8), second.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Best-effort validation for present-but-invalid server.url values.
     * Ensures the URL is a valid absolute HTTP/HTTPS URL with a host component.
     * Note: if server.url is completely missing, other beans that inject it without a default will fail first.
     */
    private void validateServerUrl() {
        if (serverUrl == null || serverUrl.isBlank()) {
            log.warn("server.url is not configured. Other components may fail to start.");
            return;
        }
        try {
            URI uri = URI.create(serverUrl);
            String scheme = uri.getScheme();
            if (uri.isOpaque() || !uri.isAbsolute() || (!HTTP_SCHEME.equals(scheme) && !HTTPS_SCHEME.equals(scheme)) || uri.getHost() == null) {
                String errorMessage = "server.url '%s' is not a valid absolute HTTP/HTTPS URL with a host. It is used in rendered links and asset URLs.".formatted(serverUrl);
                log.error(errorMessage);
                throw new IllegalStateException(errorMessage);
            }
        }
        catch (IllegalArgumentException e) {
            String errorMessage = "server.url '%s' is not a valid URL: %s".formatted(serverUrl, e.getMessage());
            log.error(errorMessage);
            throw new IllegalStateException(errorMessage, e);
        }
    }

    /**
     * Validates the passkey configuration at startup.
     * Throws a {@link ConflictingPasskeyConfigurationException} if the configuration is invalid.
     */
    private void validatePasskeyConfiguration() {
        boolean passkeyEnabled = artemisConfigHelper.isPasskeyEnabled(environment);
        boolean passkeyRequiredForAdminFeatures = isPasskeyRequiredForAdministratorFeatures;

        if (passkeyRequiredForAdminFeatures && !passkeyEnabled) {
            String errorMessage = ("Invalid passkey configuration: %s is set to true, but %s is set to false. "
                    + "Passkey must be enabled if it is required for administrator features. "
                    + "Please update your application configuration files to enable passkey or disable the requirement for administrator features.")
                    .formatted(Constants.PASSKEY_REQUIRE_FOR_ADMINISTRATOR_FEATURES_PROPERTY_NAME, Constants.PASSKEY_ENABLED_PROPERTY_NAME);
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
                String errorMessage = "Internal admin username is too short. Minimum length is %d characters, but provided username has %d characters."
                        .formatted(USERNAME_MIN_LENGTH, internalAdminUsername.length());
                log.error(errorMessage);
                throw new InvalidAdminConfigurationException(errorMessage, "username", "artemis.user-management.internal-admin.username", "***hidden***",
                        "Must be between %d and %d characters".formatted(USERNAME_MIN_LENGTH, USERNAME_MAX_LENGTH));
            }

            if (internalAdminUsername.length() > USERNAME_MAX_LENGTH) {
                String errorMessage = "Internal admin username is too long. Maximum length is %d characters, but provided username has %d characters."
                        .formatted(USERNAME_MAX_LENGTH, internalAdminUsername.length());
                log.error(errorMessage);
                throw new InvalidAdminConfigurationException(errorMessage, "username", "artemis.user-management.internal-admin.username", "***hidden***",
                        "Must be between %d and %d characters".formatted(USERNAME_MIN_LENGTH, USERNAME_MAX_LENGTH));
            }

            // Validate password length
            if (internalAdminPassword.length() < PASSWORD_MIN_LENGTH) {
                String errorMessage = "Internal admin password is too short. Minimum length is %d characters, but provided password has %d characters."
                        .formatted(PASSWORD_MIN_LENGTH, internalAdminPassword.length());
                log.error(errorMessage);
                throw new InvalidAdminConfigurationException(errorMessage, "password", "artemis.user-management.internal-admin.password", "***hidden***",
                        "Must be at least %d characters".formatted(PASSWORD_MIN_LENGTH));
            }

            log.info("Internal admin configuration validated successfully");
        }
    }

    /**
     * Validates the Weaviate configuration when Weaviate is enabled.
     * Throws a {@link WeaviateConfigurationException} if required properties are missing or invalid.
     */
    private void validateWeaviateConfiguration() {
        if (!weaviateEnabled) {
            return;
        }
        if (isOpenApiDocsGeneration) {
            log.info("Skipping Weaviate configuration validation during OpenAPI docs generation");
            return;
        }

        List<String> invalidProperties = new ArrayList<>();

        if (weaviateHost == null || weaviateHost.isBlank()) {
            invalidProperties.add("artemis.weaviate.http-host (must not be empty)");
        }

        if (!isValidPort(weaviatePort)) {
            invalidProperties.add("artemis.weaviate.http-port (must be between " + MIN_PORT + " and " + MAX_PORT + ")");
        }

        if (!isValidPort(weaviateGrpcPort)) {
            invalidProperties.add("artemis.weaviate.grpc-port (must be between " + MIN_PORT + " and " + MAX_PORT + ")");
        }

        String effectiveScheme = null;
        if (weaviateScheme == null || weaviateScheme.isBlank()) {
            invalidProperties.add("artemis.weaviate.scheme (must be configured when Weaviate is enabled)");
        }
        else if (!HTTP_SCHEME.equals(weaviateScheme) && !HTTPS_SCHEME.equals(weaviateScheme)) {
            invalidProperties.add("artemis.weaviate.scheme (must be '" + HTTP_SCHEME + "' or '" + HTTPS_SCHEME + "')");
        }
        else {
            effectiveScheme = weaviateScheme;
        }

        if (weaviateVectorizerModule == null || weaviateVectorizerModule.isBlank()) {
            invalidProperties.add("artemis.weaviate.vectorizer-module (must be configured when Weaviate is enabled)");
        }
        else if (!SupportedVectorizer.isSupported(weaviateVectorizerModule)) {
            invalidProperties.add("artemis.weaviate.vectorizer-module (must be one of " + Arrays.toString(SupportedVectorizer.values()) + ")");
        }

        boolean shouldValidateOpenAiSpecificProperties = TEXT2VEC_OPENAI.configValue().equals(weaviateVectorizerModule);
        if (shouldValidateOpenAiSpecificProperties) {
            if (!StringUtils.hasText(weaviateOpenAiBaseUrl)) {
                invalidProperties.add("artemis.weaviate.open-ai-base-url (must be configured when using " + TEXT2VEC_OPENAI.configValue() + " vectorizer)");
            }
            else {
                try {
                    URI uri = URI.create(weaviateOpenAiBaseUrl);
                    String scheme = uri.getScheme();
                    boolean isInvalidUrl = !uri.isAbsolute() || (!"http".equals(scheme) && !"https".equals(scheme));
                    if (isInvalidUrl) {
                        invalidProperties.add("artemis.weaviate.open-ai-base-url (must be a valid absolute URL with http or https scheme when using "
                                + TEXT2VEC_OPENAI.configValue() + " vectorizer)");
                    }
                }
                catch (IllegalArgumentException e) {
                    invalidProperties.add("artemis.weaviate.open-ai-base-url (must be a valid absolute URL with http or https scheme when using " + TEXT2VEC_OPENAI.configValue()
                            + " vectorizer)");
                }
            }
            if (!StringUtils.hasText(weaviateGpuApiKey)) {
                invalidProperties
                        .add("artemis.weaviate.gpu-api-key (must be configured when using " + TEXT2VEC_OPENAI.configValue() + " vectorizer, use a dummy value for Ollama)");
            }
        }

        if (!invalidProperties.isEmpty()) {
            String errorMessage = "Invalid Weaviate configuration: Weaviate is enabled but the following properties are missing or invalid: "
                    + String.join(", ", invalidProperties);
            log.error(errorMessage);
            throw new WeaviateConfigurationException(errorMessage, invalidProperties);
        }

        boolean secure = HTTPS_SCHEME.equals(effectiveScheme);
        log.info("Weaviate is enabled and configured with host: {}:{} (gRPC port: {}, secure: {}, scheme: {}, vectorizer: {})", weaviateHost, weaviatePort, weaviateGrpcPort,
                secure, effectiveScheme, weaviateVectorizerModule);
    }

    public static boolean isValidPort(int port) {
        return port >= MIN_PORT && port <= MAX_PORT;
    }
}
