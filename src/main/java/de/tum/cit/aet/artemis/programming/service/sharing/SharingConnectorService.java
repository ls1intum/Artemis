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
 * Service to support Sharing Platform functionality as a plugin.
 *
 * @see <a href="https://sharing-codeability.uibk.ac.at/sharing/codeability-sharing-platform/-/wikis/technical/Plugin-Interface">Plugin Tutorial</a>
 */
@Service
@Conditional(SharingEnabled.class)
@Lazy
public class SharingConnectorService {

    public static final int HEALTH_HISTORY_LIMIT = 10;

    /**
     * just to signal a missing/inconsistent sharing configuration to the sharing connector service.
     */
    public static final String UNKNOWN_INSTALLATION_NAME = "unknown installation name";

    /**
     * protected, to be reused in tests.
     */
    protected static final int MAX_API_KEY_LENGTH = 200;

    public static class HealthStatus {

        private final Instant timeStamp = Instant.now();

        private final String statusMessage;

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
     * holds the current status and the last connection timestamp
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
     * Base url for callbacks to Sharing Platform
     */
    private URL sharingApiBaseUrl = null;

    /**
     * cmd
     * installation name forwarded in config for Sharing Platform
     */
    private String installationName = UNKNOWN_INSTALLATION_NAME; // to be set after first contact with sharing platform

    @Value("${artemis.sharing.apikey:#{null}}")
    private String sharingApiKey;

    @Value("${artemis.sharing.actionname:Export to Artemis@somewhere}")
    private String actionName;

    /**
     * the url of the sharing platform.
     * Only needed for initial trigger a configuration exchange during startup.
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
     * Sets the apiKey explicitly. For test purpose only.
     *
     * @param sharingApiKey the explicit api key
     */
    public void setSharingApiKey(String sharingApiKey) {
        this.sharingApiKey = sharingApiKey;
    }

    public String getSharingApiKeyOrNull() {
        return sharingApiKey;
    }

    public boolean isSharingApiBaseUrlPresent() {
        return sharingApiBaseUrl != null;
    }

    /**
     * Returns the configuration forwarded to sharing plugin.
     *
     * @param apiBaseUrl       the base url of the sharing application api (for callbacks)
     * @param installationName an optional descriptive name of the sharing application
     * @return the sharing plugin config
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
     * validates the api key transferred from sharing platform.
     *
     * @param apiKey the key to check
     * @return true if the api key (resp. the bearer token) is valid.
     *
     */
    public boolean validateApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() > MAX_API_KEY_LENGTH) {
            // this is just in case, somebody tries an attack
            lastHealthStati.add(new HealthStatus("Failed api Key validation"));

            return false;
        }
        Pattern p = Pattern.compile("^Bearer\\s+(\\S+)$");
        Matcher m = p.matcher(apiKey);
        if (m.matches()) {
            apiKey = m.group(1);
        }

        boolean success = Objects.equals(sharingApiKey, apiKey);
        if (!success) {
            lastHealthStati.add(new HealthStatus("Failed api Key validation (unequal)"));
        }
        return success;
    }

    /**
     * After bean creation, we trigger an reinitialization from the Sharing Platform:
     * i.e. we query the Sharing Platform to send a
     * new config request immediately, not waiting for the next scheduled request.
     * It starts a background thread via the spring task scheduler in order not to block application startup.
     */
    @PostConstruct
    public void triggerSharingReinitAfterApplicationStart() {
        taskScheduler.schedule(this::triggerReinit, Instant.now().plusSeconds(10));
    }

    /**
     * Shuts down the service.
     * Currently just for test purposes
     */
    void shutDown() {
        sharingApiBaseUrl = null;
        this.lastHealthStati.add(new HealthStatus("shutdown requested"));
        this.lastHealthStati.resetLastConnect();
    }

    /**
     * request a reinitialization of the sharing platform.
     * Not for external use, package visible for test purpose only.
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

    public HealthStatusWithHistory getLastHealthStati() {
        return lastHealthStati;
    }

}
