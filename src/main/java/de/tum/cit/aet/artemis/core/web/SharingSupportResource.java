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

    private static final Logger log = LoggerFactory.getLogger(SharingSupportResource.class);

    private static final String SHARINGCONFIG_RESOURCE_PATH = "config";

    public static final String SHARINGCONFIG_RESOURCE_IS_ENABLED = SHARINGCONFIG_RESOURCE_PATH + "/is-enabled";

    private final SharingConnectorService sharingConnectorService;

    public SharingSupportResource(SharingConnectorService sharingConnectorService) {
        this.sharingConnectorService = sharingConnectorService;
    }

    /**
     * GET api/core/sharing/config
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
     * GET api/core/sharing/config/is-enabled
     * Return a boolean value representing the current profile state of Sharing
     *
     * @return Status 200 if a Sharing ApiBaseUrl is present, in case that sharing is not enabled Http-Status 503 is signalled, because
     *         this resource is not available!
     */
    @GetMapping(SHARINGCONFIG_RESOURCE_IS_ENABLED)
    public ResponseEntity<Boolean> isSharingEnabled() {
        return ResponseEntity.ok(sharingConnectorService.isSharingApiBaseUrlPresent());
    }
}
