package de.tum.cit.aet.artemis.core.web;

import static de.tum.cit.aet.artemis.core.config.Constants.SHARINGCONFIG_RESOURCE_IS_ENABLED;
import static de.tum.cit.aet.artemis.core.config.Constants.SHARINGCONFIG_RESOURCE_PATH;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Optional;

import org.codeability.sharing.plugins.api.SharingPluginConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.exercise.service.sharing.SharingConnectorService;

/**
 * REST controller for the exchange of configuration data between artemis and the sharing platform.
 */
@Validated
@RestController
@RequestMapping("/api")
@Profile("sharing")
public class SharingSupportResource {

    /**
     * the logger
     */
    private final Logger log = LoggerFactory.getLogger(SharingSupportResource.class);

    /**
     * the sharing plugin service
     */
    private final SharingConnectorService sharingConnectorService;

    /**
     * constructor
     *
     * @param sharingConnectorService the sharing connector service
     */
    @SuppressWarnings("unused")
    public SharingSupportResource(SharingConnectorService sharingConnectorService) {
        this.sharingConnectorService = sharingConnectorService;
    }

    /**
     * Returns Sharing Plugin configuration to be used in context with Artemis.
     * This configuration is requested by the sharing platform on a regular basis.
     * It is secured by the common secret api key token transferred by Authorization header.
     *
     * @param sharingApiKey    the common secret api key token (transfered by Authorization header).
     * @param apiBaseUrl       the base url of the sharing application api (for callbacks)
     * @param installationName a descriptive name of the sharing application
     *
     * @return Sharing Plugin configuration
     * @see <a href="https://sharing-codeability.uibk.ac.at/development/sharing/codeability-sharing-platform/-/wikis/Setup/Connector-Interface-Setup">Connector Interface Setup</a>
     *
     */
    @GetMapping(SHARINGCONFIG_RESOURCE_PATH)
    public ResponseEntity<SharingPluginConfig> getConfig(@RequestHeader("Authorization") Optional<String> sharingApiKey, @RequestParam String apiBaseUrl,
            @RequestParam String installationName) {
        if (sharingApiKey.isPresent() && sharingConnectorService.validate(sharingApiKey.get())) {
            log.info("Delivered Sharing Config ");
            URL apiBaseUrl1;
            try {
                apiBaseUrl1 = URI.create(apiBaseUrl).toURL();
            }
            catch (MalformedURLException e) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
            }
            return ResponseEntity.ok(sharingConnectorService.getPluginConfig(apiBaseUrl1, installationName));
        }
        log.warn("Received wrong or missing api key");
        return ResponseEntity.status(401).body(null);
    }

    /**
     * Return a boolean value representing the current state of Sharing
     *
     * @return Status 200 if a Sharing ApiBaseUrl is present, Status 503 otherwise
     */
    @GetMapping(SHARINGCONFIG_RESOURCE_IS_ENABLED)
    public ResponseEntity<Boolean> isSharingEnabled() {
        if (sharingConnectorService.isSharingApiBaseUrlPresent()) {
            return ResponseEntity.status(200).body(true);
        }
        return ResponseEntity.status(503).body(false);
    }
}
