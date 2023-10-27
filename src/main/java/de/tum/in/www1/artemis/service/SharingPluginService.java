package de.tum.in.www1.artemis.service;

import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.codeability.sharing.plugins.api.SharingPluginConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Service to support Sharing Platform functionality as a plugin.
 *
 * @link https://sharing-codeability.uibk.ac.at/sharing/codeability-sharing-platform/-/wikis/technical/Plugin-Interface
 */
@Service
@Profile("sharing")
public class SharingPluginService {

    private final Logger log = LoggerFactory.getLogger(SharingPluginService.class);

    /**
     * Base url for callbacks
     */
    @Value("${artemis.sharing.api-url:#{null}}")
    private URL sharingApiBaseUrl;

    @Value("${artemis.sharing.api-key:#{null}}")
    private String sharingApiKey;

    private String installationName;

    private ProfileService profileService;

    public SharingPluginService(ProfileService profileService) {
        this.profileService = profileService;
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
     */
    public SharingPluginConfig getPluginConfig() {
        SharingPluginConfig.Action action = new SharingPluginConfig.Action("Import", "/sharing/import", "Export to Artemis",
                "metadata.format.stream().anyMatch(entry->entry=='artemis' || entry=='Artemis').get()");
        return new SharingPluginConfig("Artemis Sharing Connector", new SharingPluginConfig.Action[] { action });
    }

    /**
     * Method used to validate the given authorization apiKey from Sharing
     *
     * @param apiKey the Key to validate
     * @return true if valid, false otherwise
     */
    public boolean validate(String apiKey) {
        Pattern p = Pattern.compile("Bearer\\s*(.+)");
        Matcher m = p.matcher(apiKey);
        if (m.matches()) {
            apiKey = m.group(1);
        }

        return sharingApiKey.equals(apiKey);
    }
}
