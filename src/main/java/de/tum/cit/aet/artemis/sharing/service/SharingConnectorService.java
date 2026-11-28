package de.tum.cit.aet.artemis.sharing.service;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.annotation.PostConstruct;

import org.apache.commons.codec.digest.DigestUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import de.tum.cit.aet.artemis.sharing.config.SharingEnabled;
import de.tum.cit.aet.artemis.sharing.connector.SharingPluginConfig;

/**
 * Service that integrates Artemis with the external Sharing Platform via the plugin interface.
 *
 * <p>
 * Exposes configuration, validates API keys, records lightweight health information, and
 * optionally triggers a reinitialization handshake with the Sharing Platform at startup.
 * </p>
 *
 * @see <a href="https://sharing-codeability.uibk.ac.at/sharing/codeability-sharing-platform/-/wikis/technical/Plugin-Interface">
 *      Plugin Tutorial</a>
 */
@Service
@Conditional(SharingEnabled.class)
@Lazy
public class SharingConnectorService {

    /**
     * Maximum number of recent health entries to keep in memory.
     */
    public static final int HEALTH_HISTORY_LIMIT = 10;

    /**
     * Marker used when the installation name cannot be derived from configuration or hostname.
     * <p>
     * Signals a missing or inconsistent sharing configuration to the connector.
     * </p>
     */
    public static final String UNKNOWN_INSTALLATION_NAME = "unknown installation name";

    /**
     * Hard upper bound for accepted API key length.
     * <p>
     * Restricts unbounded input and mitigates trivial abuse in validation.
     * </p>
     * <p>
     * <b>Visibility:</b> protected to be reusable in tests.
     * </p>
     */
    protected static final int MAX_API_KEY_LENGTH = 200;

    record HealthEntry(Instant timestamp, String message) {
    }

    record HealthSnapshot(List<HealthEntry> entries, @Nullable Instant lastConnect) {

        HealthSnapshot {
            entries = List.copyOf(entries);
        }
    }

    private final List<HealthEntry> healthHistory = new ArrayList<>(HEALTH_HISTORY_LIMIT);

    @Nullable
    private Instant lastConnect;

    private static final Logger log = LoggerFactory.getLogger(SharingConnectorService.class);

    /** A bearer token in an authorization header, with the token itself as the one group. */
    private static final Pattern BEARER_TOKEN = Pattern.compile("^Bearer\\s+(\\S+)$");

    /**
     * Base URL used by Artemis to call back into the Sharing Platform.
     * <p>
     * Set on first configuration exchange and retained for subsequent operations.
     * </p>
     */
    private URL sharingApiBaseUrl = null;

    /**
     * Installation name forwarded to the Sharing Platform and shown in its UI.
     * <p>
     * Initialized to {@link #UNKNOWN_INSTALLATION_NAME} and resolved on first contact
     * from the optional request parameter, {@code HOSTNAME} env var, or local host name.
     * </p>
     */
    private String installationName = UNKNOWN_INSTALLATION_NAME; // to be set after first contact with sharing platform

    @Value("${artemis.sharing.apikey:#{null}}")
    @Nullable
    private String sharingApiKey;

    @Value("${artemis.sharing.actionname:Export to Artemis@somewhere}")
    private String actionName;

    /**
     * Base URL of the Sharing Platform.
     * <p>
     * Used only to actively trigger a configuration refresh during startup.
     * </p>
     */
    @Value("${artemis.sharing.serverurl:#{null}}")
    private String sharingUrl;

    private final TaskScheduler taskScheduler;

    /**
     * installation name forwarded in config for Sharing Platform, used to differentiate in Sharing Platform
     *
     * @return the name of this artemis installation (as shown in Sharing Platform)
     */
    public String getInstallationName() {
        return installationName;
    }

    private final RestClient restClient;

    public SharingConnectorService(@Qualifier("sharingRestClient") RestClient restClient, TaskScheduler taskScheduler) {
        this.restClient = restClient;
        this.taskScheduler = taskScheduler;
    }

    public void setSharingApiBaseUrl(URL sharingApiBaseUrl) {
        this.sharingApiBaseUrl = sharingApiBaseUrl;
    }

    public URL getSharingApiBaseUrlOrNull() {
        return sharingApiBaseUrl;
    }

    /**
     * Checks that the given base URL addresses the Sharing Platform API this instance is connected to.
     * <p>
     * The connection is held per node. A node that has not been connected yet has no reference to compare with,
     * so the check passes there and the callers rely on the checksum of the request.
     *
     * @param apiBaseUrl the base URL received with a request
     * @return false if the URL is malformed or differs from the configured one, true otherwise
     */
    public boolean matchesConfiguredApiBaseUrl(String apiBaseUrl) {
        URL configured = sharingApiBaseUrl;
        if (configured == null) {
            return true;
        }
        if (apiBaseUrl == null) {
            return false;
        }
        try {
            URL candidate = URI.create(apiBaseUrl).toURL();
            return candidate.getProtocol().equalsIgnoreCase(configured.getProtocol()) && candidate.getHost().equalsIgnoreCase(configured.getHost())
                    && effectivePort(candidate) == effectivePort(configured) && trimTrailingSlashes(candidate.getPath()).equals(trimTrailingSlashes(configured.getPath()));
        }
        catch (IllegalArgumentException | MalformedURLException e) {
            return false;
        }
    }

    private static int effectivePort(URL url) {
        return url.getPort() == -1 ? url.getDefaultPort() : url.getPort();
    }

    private static String trimTrailingSlashes(String path) {
        int end = path.length();
        while (end > 0 && path.charAt(end - 1) == '/') {
            end--;
        }
        return path.substring(0, end);
    }

    /**
     * Overrides the configured API key. Intended for tests.
     *
     * @param sharingApiKey the explicit API key to set
     */
    public void setSharingApiKey(String sharingApiKey) {
        this.sharingApiKey = sharingApiKey;
    }

    @Nullable
    public String getSharingApiKey() {
        return sharingApiKey;
    }

    public boolean isSharingApiBaseUrlPresent() {
        return sharingApiBaseUrl != null;
    }

    /**
     * Builds and returns the {@link SharingPluginConfig} to be consumed by the Sharing Platform.
     * <p>
     * Also persists the provided {@code apiBaseUrl}, resolves and stores the installation name, and
     * records a health entry and connection timestamp.
     * </p>
     *
     * @param apiBaseUrl       base URL of the Sharing Platform API (used for callbacks)
     * @param installationName optional human-readable installation identifier; if empty, falls back to
     *                             {@code HOSTNAME} environment variable or the local canonical host name
     * @return a plugin configuration containing the Artemis import action and metadata
     */
    public SharingPluginConfig getPluginConfig(URL apiBaseUrl, @SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<String> installationName) {
        this.sharingApiBaseUrl = apiBaseUrl;
        this.installationName = installationName.or(() -> Optional.ofNullable(System.getenv("HOSTNAME"))).orElseGet(SharingConnectorService::localHostName);
        // this defines the export action in the sharing platform, by defining a filter to select all exercises in Artemis format.
        SharingPluginConfig.Action action = new SharingPluginConfig.Action("Import", "/sharing/import", actionName,
                "metadata.format.stream().anyMatch(entry->entry=='artemis' || entry=='Artemis').get()");
        synchronized (this) {
            lastConnect = recordHealth("Delivered configuration to Sharing Platform");
        }
        return new SharingPluginConfig("Artemis Sharing Connector", List.of(action));
    }

    private static String localHostName() {
        try {
            return InetAddress.getLocalHost().getCanonicalHostName();
        }
        catch (UnknownHostException e) {
            log.warn("Failed to determine hostname", e);
            return UNKNOWN_INSTALLATION_NAME;
        }
    }

    /**
     * Validates an API key supplied by the Sharing Platform.
     * <p>
     * Accepts either a raw token or a {@code Bearer &lt;token&gt;} header value. Length is bounded by
     * {@link #MAX_API_KEY_LENGTH}. On failure, a health entry is recorded.
     * </p>
     *
     * @param apiKey the provided credential; raw token or {@code Bearer} header value
     * @return {@code true} if the token matches the configured {@code artemis.sharing.apikey}; otherwise {@code false}
     */
    public boolean validateApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() > MAX_API_KEY_LENGTH) {
            // this is just in case, somebody tries an attack
            recordHealth("Failed api Key validation");

            return false;
        }
        Matcher matcher = BEARER_TOKEN.matcher(apiKey);
        if (matcher.matches()) {
            apiKey = matcher.group(1);
        }

        boolean success = sharingApiKey != null && MessageDigest.isEqual(DigestUtils.sha256(sharingApiKey), DigestUtils.sha256(apiKey));
        if (!success) {
            recordHealth("Failed api Key validation (unequal)");
        }
        return success;
    }

    /**
     * Schedules a one-time reinitialization request shortly after application startup.
     * <p>
     * Uses the Spring {@link TaskScheduler} to avoid blocking startup. If {@code artemis.sharing.serverurl}
     * is unset, no request is made.
     * </p>
     */
    @PostConstruct
    public void triggerSharingReinitAfterApplicationStart() {
        taskScheduler.schedule(this::triggerReinit, Instant.now().plusSeconds(10));
    }

    /**
     * Shuts down the connector service.
     * <p>
     * Clears the cached API base URL and health {@code lastConnect} marker, and records a status entry.
     * Primarily used in tests.
     * </p>
     */
    synchronized void shutDown() {
        sharingApiBaseUrl = null;
        recordHealth("shutdown requested");
        lastConnect = null;
    }

    /**
     * Requests a reinitialization from the Sharing Platform.
     * <p>
     * Package-private for tests. If {@code artemis.sharing.serverurl} is configured, sends a
     * {@code GET /api/pluginIF/v0.1/reInitialize?apiKey=...} call using the configured {@link RestClient}.
     * Records success or failure in health history and logs warnings on failure.
     * </p>
     */
    void triggerReinit() {
        if (sharingUrl == null) {
            return;
        }

        log.info("Requesting reinitialization from Sharing Platform");
        recordHealth("Requested reinitialization from Sharing Platform");
        try {
            String reInitUrlWithApiKey = UriComponentsBuilder.fromUriString(sharingUrl).pathSegment("api", "pluginIF", "v0.1", "reInitialize").queryParam("apiKey", sharingApiKey)
                    .encode().toUriString();
            if (Boolean.TRUE.equals(restClient.get().uri(reInitUrlWithApiKey).retrieve().body(Boolean.class))) {
                return;
            }
        }
        catch (RuntimeException _) {
            // A client exception can include the full request URL with the API key.
        }
        String failure = "Failed to request reinitialization from Sharing Platform";
        log.warn(failure);
        recordHealth(failure);
    }

    private synchronized Instant recordHealth(String message) {
        var entry = new HealthEntry(Instant.now(), message);
        if (healthHistory.size() == HEALTH_HISTORY_LIMIT) {
            healthHistory.removeFirst();
        }
        healthHistory.add(entry);
        return entry.timestamp();
    }

    synchronized HealthSnapshot getHealthSnapshot() {
        return new HealthSnapshot(healthHistory, lastConnect);
    }
}
