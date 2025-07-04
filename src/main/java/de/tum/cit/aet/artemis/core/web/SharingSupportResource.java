package de.tum.cit.aet.artemis.core.web;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Optional;

import org.codeability.sharing.plugins.api.SharingPluginConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.programming.service.sharing.SharingConnectorService;

/**
 * REST controller for the exchange of configuration data between artemis and the sharing platform.
 */
@RestController
@RequestMapping("api/core/sharing/")
@Profile(Constants.PROFILE_SHARING)
@Lazy
public class SharingSupportResource {

    /**
     * the logger
     */
    private static final Logger log = LoggerFactory.getLogger(SharingSupportResource.class);

    /**
     * the sharing configuration resource path for sharing config request
     */
    private static final String SHARINGCONFIG_RESOURCE_PATH = "config";

    /**
     * the sharing configuration resource path for rest request, iff sharing profile is enabled
     */
    public static final String SHARINGCONFIG_RESOURCE_IS_ENABLED = SHARINGCONFIG_RESOURCE_PATH + "/is-enabled";

    /**
     * the sharing plugin service
     */
    private final SharingConnectorService sharingConnectorService;

    /**
     * @param sharingConnectorService the sharing connector service
     */
    public SharingSupportResource(SharingConnectorService sharingConnectorService) {
        this.sharingConnectorService = sharingConnectorService;
    }

    /**
     * Returns Sharing Plugin configuration to be used in context with Artemis.
     * This configuration is requested by the sharing platform in general every 10 minutes.
     * It is secured by the common secret api key token transferred by Authorization header.
     *
     * @param sharingApiKey    the common secret api key token (transferred by Authorization header).
     * @param apiBaseUrl       the base url of the sharing application api (for callbacks)
     * @param installationName a descriptive name of the sharing application
     *
     * @return Sharing Plugin configuration
     * @see <a href="https://sharing-codeability.uibk.ac.at/development/sharing/codeability-sharing-platform/-/wikis/Setup/Connector-Interface-Setup">Connector Interface Setup</a>
     *
     */
    @GetMapping(SHARINGCONFIG_RESOURCE_PATH)
    public ResponseEntity<SharingPluginConfig> getConfig(@SuppressWarnings("OptionalUsedAsFieldOrParameterType") @RequestHeader("Authorization") Optional<String> sharingApiKey,
            @RequestParam String apiBaseUrl, @SuppressWarnings("OptionalUsedAsFieldOrParameterType") @RequestParam Optional<String> installationName) {
        if (sharingApiKey.isPresent() && sharingConnectorService.validate(sharingApiKey.get())) {
            log.info("Delivered Sharing Config ");
            URL parsedApiBaseUrl;
            try {
                parsedApiBaseUrl = URI.create(apiBaseUrl).toURL();
            }
            catch (IllegalArgumentException | MalformedURLException e) {
                log.error("Bad URL", e);
                return ResponseEntity.badRequest().build();
            }
            return ResponseEntity.ok(sharingConnectorService.getPluginConfig(parsedApiBaseUrl, installationName));
        }
        log.warn("Received wrong or missing api key");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
    }

    /**
     * Return a boolean value representing the current state of Sharing
     *
     * @return Status 200 if a Sharing ApiBaseUrl is present, Status 503 otherwise
     */
    @GetMapping(SHARINGCONFIG_RESOURCE_IS_ENABLED)
    public ResponseEntity<Boolean> isSharingEnabled() {
        return ResponseEntity.ok(sharingConnectorService.isSharingApiBaseUrlPresent());
    }
}
