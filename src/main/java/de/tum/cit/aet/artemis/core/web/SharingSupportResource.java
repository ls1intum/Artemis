package de.tum.cit.aet.artemis.core.web;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.codeability.sharing.plugins.api.SharingPluginConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.programming.service.sharing.SharingConnectorService;
import de.tum.cit.aet.artemis.programming.service.sharing.SharingEnabled;

/**
 * REST controller for the exchange of configuration data between artemis and the sharing platform.
 */
@RestController
@RequestMapping("api/core/sharing/")
@Conditional(SharingEnabled.class)
@Lazy
public class SharingSupportResource {

    private static final Logger log = LoggerFactory.getLogger(SharingSupportResource.class);

    private static final String SHARINGCONFIG_RESOURCE_PATH = "config";

    // public, because also used by ExerciseSharingResourceImportTest in other (test) package.
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
     * @param installationName a descriptive name of the sharing application (optional)
     *
     * @return Sharing Plugin configuration
     * @see <a href="https://sharing-codeability.uibk.ac.at/development/sharing/codeability-sharing-platform/-/wikis/Setup/Connector-Interface-Setup">Connector Interface Setup</a>
     *
     */
    @GetMapping(SHARINGCONFIG_RESOURCE_PATH)
    public ResponseEntity<SharingPluginConfig> getConfig(@SuppressWarnings("OptionalUsedAsFieldOrParameterType") @RequestHeader("Authorization") Optional<String> sharingApiKey,
            @RequestParam String apiBaseUrl, @SuppressWarnings("OptionalUsedAsFieldOrParameterType") @RequestParam Optional<String> installationName) {
        final String BEARER_PREFIX = "Bearer ";
        final Optional<String> token = sharingApiKey.map(key -> key.startsWith(BEARER_PREFIX) ? key.substring(BEARER_PREFIX.length()) : key);
        if (token.isPresent() && sharingConnectorService.validateApiKey(token.get())) {
            log.info("Delivered Sharing Config");
            URL parsedApiBaseUrl;
            try {
                parsedApiBaseUrl = URI.create(apiBaseUrl).toURL();
                if (StringUtils.isEmpty(parsedApiBaseUrl.getHost())) {
                    log.warn("Rejected config request: apiBaseUrl missing host");
                    return ResponseEntity.badRequest().build();
                }
                final String protocol = parsedApiBaseUrl.getProtocol();
                if (!"https".equalsIgnoreCase(protocol) && !"http".equalsIgnoreCase(protocol)) {
                    log.warn("Rejected config request: disallowed scheme {}", protocol);
                    return ResponseEntity.badRequest().build();
                }
            }
            catch (IllegalArgumentException | MalformedURLException e) {
                log.error("Bad URL", e);
                return ResponseEntity.badRequest().build();
            }
            return ResponseEntity.ok(sharingConnectorService.getPluginConfig(parsedApiBaseUrl, installationName));
        }
        log.warn("Received wrong or missing api key");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    /**
     * GET api/core/sharing/config/is-enabled
     * This method returns three different responses:
     *
     * <ul>
     * <li>true, if the sharing profile is enabled, and the connection to the sharing platform is established.</li>
     * <li>false. if the sharing profile is enabled, however the connection to the sharing platform is not (yet) established.</li>
     * <li>If the sharing profile is not enabled, this endpoint is not available (HTTP 404).</li>
     * </ul>
     * The last two cases must be interpreted as sharing is not enabled.
     *
     * @return Status 200 with true if a Sharing ApiBaseUrl is present, or false if the Sharing profile is enabled but not yet connected.
     *         If the Sharing profile is disabled, the endpoint is not available (HTTP 404).
     */
    @GetMapping(SHARINGCONFIG_RESOURCE_IS_ENABLED)
    public ResponseEntity<Boolean> isSharingEnabled() {
        return ResponseEntity.ok(sharingConnectorService.isSharingApiBaseUrlPresent());
    }
}
