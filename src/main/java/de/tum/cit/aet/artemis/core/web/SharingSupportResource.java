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
 * REST controller that exposes Artemis ↔ Sharing Platform integration endpoints.
 * <p>
 * Loaded only when sharing is enabled ({@link SharingEnabled}); otherwise the controller
 * is absent and all endpoints return HTTP 404.
 * </p>
 *
 * <h2>Security</h2>
 * All endpoints require a shared secret API key sent via the {@code Authorization} header
 * using the {@code Bearer <token>} scheme.
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
     * <p>
     * Returns the {@link SharingPluginConfig} that the sharing platform should use with Artemis.
     * <p>
     * The sharing platform polls this endpoint periodically (typically every ~10 minutes).
     * Requests must be authenticated via {@code Authorization: Bearer <token>}.
     * The {@code apiBaseUrl} is validated to contain a host and to use {@code https} or {@code http}.
     * </p>
     *
     * <h3>Request</h3>
     * <ul>
     * <li><b>Header</b> {@code Authorization}: {@code Bearer <token>} (required)</li>
     * <li><b>Query</b> {@code apiBaseUrl}: base URL of the sharing platform API used for callbacks (required)</li>
     * <li><b>Query</b> {@code installationName}: human-readable installation identifier (optional)</li>
     * </ul>
     *
     * <h3>Responses</h3>
     * <ul>
     * <li><b>200 OK</b>: returns the resolved {@link SharingPluginConfig}.</li>
     * <li><b>400 Bad Request</b>: malformed or disallowed {@code apiBaseUrl}
     * (missing host, unsupported scheme, or invalid URL).</li>
     * <li><b>401 Unauthorized</b>: missing or invalid API key.</li>
     * </ul>
     *
     * @param sharingApiKey    the {@code Authorization} header value; either {@code Bearer <token>} or the raw token
     * @param apiBaseUrl       the sharing platform API base URL used for callbacks (must include a host; scheme must be {@code https} or {@code http})
     * @param installationName optional descriptive name of the sharing platform installation
     * @return {@code 200 OK} with the plugin configuration; {@code 400} on invalid {@code apiBaseUrl};
     *         {@code 401} on missing/invalid credentials
     * @see <a href="https://sharing-codeability.uibk.ac.at/development/sharing/codeability-sharing-platform/-/wikis/Setup/Connector-Interface-Setup">Connector Interface Setup</a>
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
     * <p>
     * Indicates whether sharing support is effectively enabled and connected.
     * <p>
     * Returns {@code true} if a Sharing {@code apiBaseUrl} is configured and reachable (as determined
     * by {@link SharingConnectorService#isSharingApiBaseUrlPresent()}), otherwise {@code false}.
     * If sharing is disabled entirely, this controller is not loaded and the endpoint resolves to
     * HTTP 404.
     * </p>
     *
     * <h3>Responses</h3>
     * <ul>
     * <li><b>200 OK</b> with body {@code true}: sharing profile enabled and connection established.</li>
     * <li><b>200 OK</b> with body {@code false}: sharing profile enabled but not yet connected.</li>
     * <li><b>404 Not Found</b>: sharing profile disabled (controller not active).</li>
     * </ul>
     *
     * @return {@code ResponseEntity<Boolean>} indicating connection status when sharing is enabled
     */
    @GetMapping(SHARINGCONFIG_RESOURCE_IS_ENABLED)
    public ResponseEntity<Boolean> isSharingEnabled() {
        return ResponseEntity.ok(sharingConnectorService.isSharingApiBaseUrlPresent());
    }
}
