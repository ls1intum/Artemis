package de.tum.cit.aet.artemis.exercise.service.sharing;

import java.net.URL;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.codeability.sharing.plugins.api.SharingPluginConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
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
public class SharingConnectorService {

    private final Logger log = LoggerFactory.getLogger(SharingConnectorService.class);

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
    @Value("${artemis.sharing.api-key:#{null}}")
    private String sharingApiKey;

    /**
     * the url of the sharing platform.
     * Only needed for initial trigger an configuration exchange during startup.
     */
    @Value("${artemis.sharing.server-url:#{null}}")
    private String sharingUrl;

    /**
     * installation name for Sharing Platform
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

    public SharingConnectorService(ProfileService profileService, RestTemplate restTemplate) {
        this.profileService = profileService;
        this.restTemplate = restTemplate;
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
     */
    public boolean isSharingApiBaseUrlPresent() {
        return this.profileService.isSharing() && sharingApiBaseUrl != null;
    }

    /**
     * Returns a sharing plugin configuration.
     *
     * @param apiBaseUrl       the base url of the sharing application api (for callbacks)
     * @param installationName a descriptive name of the sharing application
     */
    public SharingPluginConfig getPluginConfig(URL apiBaseUrl, String installationName) {
        this.sharingApiBaseUrl = apiBaseUrl;
        this.installationName = installationName;
        SharingPluginConfig.Action action = new SharingPluginConfig.Action("Import", "/sharing/import", "Export to Artemis",
                "metadata.format.stream().anyMatch(entry->entry=='artemis' || entry=='Artemis').get()");
        return new SharingPluginConfig("Artemis Sharing Connector", new SharingPluginConfig.Action[] { action });
    }

    /**
     * Method used to validate the given authorizaion apiKey from Sharing
     *
     * @param apiKey the Key to validate
     * @return true if valid, false otherwise
     */
    public boolean validate(String apiKey) {
        if (apiKey == null || apiKey.length() > 200) {
            // this is just in case, somebody tries an attack
            return false;
        }
        Pattern p = Pattern.compile("Bearer\\s(.+)");
        Matcher m = p.matcher(apiKey);
        if (m.matches()) {
            apiKey = m.group(1);
        }

        return Objects.equals(sharingApiKey, apiKey);
    }

    /**
     * At (spring) application startup, we request a reinitialization of the sharing platform .
     * It starts a background thread in order not to block application startup.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void triggerSharingReinitAfterApplicationStart() {
        // we have to trigger sharing plattform reinitialization asynchronously,
        // because otherwise the main thread is blocked!
        Executors.newFixedThreadPool(1).execute(this::triggerReinit);
    }

    /**
     * request a reinitialization of the sharing platform
     */
    public void triggerReinit() {
        if (sharingUrl != null) {
            log.info("Requesting reinitialization from Sharing Platform");
            String reInitUrlWithApiKey = UriComponentsBuilder.fromHttpUrl(sharingUrl).pathSegment("api", "pluginIF", "v0.1", "reInitialize").queryParam("apiKey", sharingApiKey)
                    .encode().toUriString();
            try {
                restTemplate.getForObject(reInitUrlWithApiKey, Boolean.class);
            }
            catch (Exception e) {
                log.error("Failed to request reinitialization from Sharing Platform", e);
            }
        }
    }

}
