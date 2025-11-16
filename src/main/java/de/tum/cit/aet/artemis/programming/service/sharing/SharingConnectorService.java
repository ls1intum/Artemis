package de.tum.cit.aet.artemis.programming.service.sharing;

import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.annotation.PostConstruct;

import org.codeability.sharing.plugins.api.SharingPluginConfig;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

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

    /**
     * Immutable health entry consisting of a creation timestamp and a short status message.
     */
    public static class HealthStatus {

        private final Instant timeStamp = Instant.now();

        private final String statusMessage;

        /**
         * Creates a new health entry with the current timestamp.
         *
         * @param statusMessage human-readable status description
         */
        public HealthStatus(String statusMessage) {
            this.statusMessage = statusMessage;
        }

        public String getStatusMessage() {
            return statusMessage;
        }

        public Instant getTimeStamp() {
            return timeStamp;
        }
    }

    /**
     * Bounded in-memory history of recent {@link HealthStatus} entries plus the last successful connect time.
     * <p>
     * On insert, the oldest entry is removed when the size exceeds {@link #HEALTH_HISTORY_LIMIT}.
     * </p>
     */
    public static class HealthStatusWithHistory extends ArrayList<HealthStatus> {

        private Instant lastConnect = null;

        public HealthStatusWithHistory() {
            super(HEALTH_HISTORY_LIMIT);
        }

        @Override
        public synchronized boolean add(HealthStatus e) {
            if (this.size() >= HEALTH_HISTORY_LIMIT) {
                this.remove(0); // Remove the oldest element
            }
            return super.add(e);
        }

        public Instant getLastConnect() {
            return lastConnect;
        }

        public void setLastConnect() {
            this.lastConnect = Instant.now();
        }

        /**
         * resets the last connect time
         */
        public void resetLastConnect() {
            this.lastConnect = null;
        }

    }

    private final HealthStatusWithHistory lastHealthStati = new HealthStatusWithHistory();

    private static final Logger log = LoggerFactory.getLogger(SharingConnectorService.class);

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

    private final RestTemplate restTemplate;

    public SharingConnectorService(@Qualifier("sharingRestTemplate") RestTemplate restTemplate, TaskScheduler taskScheduler) {
        this.restTemplate = restTemplate;
        this.taskScheduler = taskScheduler;
    }

    public void setSharingApiBaseUrl(URL sharingApiBaseUrl) {
        this.sharingApiBaseUrl = sharingApiBaseUrl;
    }

    public URL getSharingApiBaseUrlOrNull() {
        return sharingApiBaseUrl;
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
        try {
            this.installationName = installationName.or(() -> Optional.ofNullable(System.getenv("HOSTNAME"))).orElse(InetAddress.getLocalHost().getCanonicalHostName());
        }
        catch (UnknownHostException e) {
            log.warn("Failed to determine hostname", e);
            this.installationName = UNKNOWN_INSTALLATION_NAME;
        }
        // this defines the export action in the sharing platform, by defining a filter to select all exercises in Artemis format.
        SharingPluginConfig.Action action = new SharingPluginConfig.Action("Import", "/sharing/import", actionName,
                "metadata.format.stream().anyMatch(entry->entry=='artemis' || entry=='Artemis').get()");
        lastHealthStati.add(new HealthStatus("Delivered Sharing Config Status to " + apiBaseUrl));
        lastHealthStati.setLastConnect();
        return new SharingPluginConfig("Artemis Sharing Connector", new SharingPluginConfig.Action[] { action });
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
            lastHealthStati.add(new HealthStatus("Failed api Key validation"));

            return false;
        }
        Pattern pattern = Pattern.compile("^Bearer\\s+(\\S+)$");
        Matcher matcher = pattern.matcher(apiKey);
        if (matcher.matches()) {
            apiKey = matcher.group(1);
        }

        boolean success = Objects.equals(sharingApiKey, apiKey);
        if (!success) {
            lastHealthStati.add(new HealthStatus("Failed api Key validation (unequal)"));
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
    void shutDown() {
        sharingApiBaseUrl = null;
        this.lastHealthStati.add(new HealthStatus("shutdown requested"));
        this.lastHealthStati.resetLastConnect();
    }

    /**
     * Requests a reinitialization from the Sharing Platform.
     * <p>
     * Package-private for tests. If {@code artemis.sharing.serverurl} is configured, sends a
     * {@code GET /api/pluginIF/v0.1/reInitialize?apiKey=...} call using the configured {@link RestTemplate}.
     * Records success or failure in health history and logs warnings on failure.
     * </p>
     */
    void triggerReinit() {
        if (sharingUrl != null) {
            log.info("Requesting reinitialization from Sharing Platform");
            lastHealthStati.add(new HealthStatus("Requested reinitialization from Sharing Platform via " + sharingUrl));
            String reInitUrlWithApiKey = UriComponentsBuilder.fromUriString(sharingUrl).pathSegment("api", "pluginIF", "v0.1", "reInitialize").queryParam("apiKey", sharingApiKey)
                    .encode().toUriString();
            try {
                boolean success = Boolean.TRUE.equals(restTemplate.getForObject(reInitUrlWithApiKey, Boolean.class));
                if (!success) {
                    log.warn("The request for connector reinitialization from Sharing Platform was not successful");
                    lastHealthStati.add(new HealthStatus("The request for connector reinitialization from Sharing Platform was not successful."));
                }
            }
            catch (Exception e) {
                log.warn("Failed to request reinitialization from Sharing Platform", e);
                lastHealthStati.add(new HealthStatus("The request for connector reinitialization from Sharing Platform was not successful: " + e.getMessage()));
            }
        }
    }

    /**
     * @return the recent health history including the last connection time
     */
    public HealthStatusWithHistory getLastHealthStati() {
        return lastHealthStati;
    }
}
