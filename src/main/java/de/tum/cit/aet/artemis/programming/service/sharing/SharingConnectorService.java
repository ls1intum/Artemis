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

import org.codeability.sharing.plugins.api.SharingPluginConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import de.tum.cit.aet.artemis.core.service.ProfileService;

/**
 * Service to support Sharing Platform functionality as a plugin.
 *
 * @see <a href="https://sharing-codeability.uibk.ac.at/sharing/codeability-sharing-platform/-/wikis/technical/Plugin-Interface">Plugin Tutorial</a>
 */
@Service
@Profile("sharing")
@Lazy
public class SharingConnectorService {

    /**
     * just to signal a missing/inconsistent sharing configuration to the sharing connector service.
     */
    public static final String UNKNOWN_INSTALLATION_NAME = "unknown installationame";

    /**
     * just a maximum check length for validation limiting
     */
    private static final int MAX_API_KEY_LENGTH = 200;

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
     * holds the current status and the last connect
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

        public void resetLastConnect() {
            this.lastConnect = null;
        }

    }

    public static final int HEALTH_HISTORY_LIMIT = 10;

    private final HealthStatusWithHistory lastHealthStati = new HealthStatusWithHistory();

    private static final Logger log = LoggerFactory.getLogger(SharingConnectorService.class);

    /**
     * Base url for callbacks
     */
    private URL sharingApiBaseUrl = null;

    /**
     * installation name for Sharing Platform
     */
    private String installationName = null;

    /**
     * the shared secret api key
     */
    @Value("${artemis.sharing.apikey:#{null}}")
    private String sharingApiKey;

    /**
     * the action name for the sharing platform
     */
    @Value("${artemis.sharing.actionname:Export to Artemis@somewhere}")
    private String actionName;

    /**
     * the url of the sharing platform.
     * Only needed for initial trigger an configuration exchange during startup.
     */
    @Value("${artemis.sharing.serverurl:#{null}}")
    private String sharingUrl;

    private final TaskExecutor taskExecutor;

    /**
     * installation name for Sharing Platform
     *
     * @return the name of this artemis installation (as shown in Sharing Platform)
     */
    public String getInstallationName() {
        return installationName;
    }

    /**
     * profile service
     */
    private final ProfileService profileService;

    /**
     * rest template for connector request
     */
    private final RestTemplate restTemplate;

    public SharingConnectorService(ProfileService profileService, RestTemplate restTemplate, TaskExecutor taskExecutor) {
        this.profileService = profileService;
        this.restTemplate = restTemplate;
        this.taskExecutor = taskExecutor;
    }

    /**
     * Used to set the Sharing ApiBaseUrl to a new one
     *
     * @param sharingApiBaseUrl the new url
     */
    public void setSharingApiBaseUrl(URL sharingApiBaseUrl) {
        this.sharingApiBaseUrl = sharingApiBaseUrl;
    }

    /**
     * Get the Sharing ApiBaseUrl if any, else Null
     *
     * @return SharingApiBaseUrl or Null
     */
    public URL getSharingApiBaseUrlOrNull() {
        return sharingApiBaseUrl;
    }

    /**
     * Used to set Sharing ApiKey to a new one
     *
     * @param sharingApiKey the new ApiKey
     */
    public void setSharingApiKey(String sharingApiKey) {
        this.sharingApiKey = sharingApiKey;
    }

    /**
     * Get Sharing ApiKey if any has been set, else Null
     *
     * @return SharingApiKey or null
     */
    public String getSharingApiKeyOrNull() {
        return sharingApiKey;
    }

    /**
     * Method used to check if a Sharing ApiBaseUrl is present
     *
     * @return true if sharing api base url is present
     */
    public boolean isSharingApiBaseUrlPresent() {
        return sharingApiBaseUrl != null;
    }

    /**
     * Returns a sharing plugin configuration.
     *
     * @param apiBaseUrl       the base url of the sharing application api (for callbacks)
     * @param installationName an optional descriptive name of the sharing application
     * @return the sharing plugin config
     */
    public SharingPluginConfig getPluginConfig(URL apiBaseUrl, Optional<String> installationName) {
        this.sharingApiBaseUrl = apiBaseUrl;
        try {
            this.installationName = installationName.or(() -> Optional.ofNullable(System.getenv("HOSTNAME"))).orElse(InetAddress.getLocalHost().getCanonicalHostName());
        }
        catch (UnknownHostException e) {
            log.warn("Failed to determine hostname", e);
            this.installationName = UNKNOWN_INSTALLATION_NAME;
        }
        SharingPluginConfig.Action action = new SharingPluginConfig.Action("Import", "/sharing/import", actionName,
                "metadata.format.stream().anyMatch(entry->entry=='artemis' || entry=='Artemis').get()");
        lastHealthStati.add(new HealthStatus("Delivered Sharing Config Status to " + apiBaseUrl));
        lastHealthStati.setLastConnect();
        return new SharingPluginConfig("Artemis Sharing Connector", new SharingPluginConfig.Action[] { action });
    }

    /**
     * Method used to validate the given authorizaion apiKey from Sharing
     *
     * @param apiKey the Key to validate
     * @return true if valid, false otherwise
     */
    public boolean validate(String apiKey) {
        if (apiKey == null || apiKey.length() > MAX_API_KEY_LENGTH) {
            // this is just in case, somebody tries an attack
            lastHealthStati.add(new HealthStatus("Failed api Key validation"));

            return false;
        }
        Pattern p = Pattern.compile("Bearer\\s(.+)");
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
     * At (spring) application startup, we request a reinitialization of the sharing platform .
     * It starts a background thread in order not to block application startup.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void triggerSharingReinitAfterApplicationStart() {
        taskExecutor.execute(this::triggerReinit);
    }

    /**
     * shuts down the service.
     * currently just for test purposes
     */
    void shutDown() {
        sharingApiBaseUrl = null;
        this.lastHealthStati.add(new HealthStatus("shutdown requested"));
        this.lastHealthStati.resetLastConnect();
    }

    /**
     * request a reinitialization of the sharing platform
     */
    private void triggerReinit() {
        if (sharingUrl != null) {
            log.info("Requesting reinitialization from Sharing Platform");
            lastHealthStati.add(new HealthStatus("Requested reinitialization from Sharing Platform via " + sharingUrl));
            String reInitUrlWithApiKey = UriComponentsBuilder.fromUriString(sharingUrl).pathSegment("api", "pluginIF", "v0.1", "reInitialize").queryParam("apiKey", sharingApiKey)
                    .encode().toUriString();
            try {
                boolean success = restTemplate.getForObject(reInitUrlWithApiKey, Boolean.class);
                if (!success) {
                    log.warn("The request for connector reinitialization from Sharing Platform was not successful");
                }
            }
            catch (Exception e) {
                log.warn("Failed to request reinitialization from Sharing Platform", e);
            }
        }
    }

    public HealthStatusWithHistory getLastHealthStati() {
        return lastHealthStati;
    }

}
