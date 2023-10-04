package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.config.Constants.*;

import java.util.Optional;

import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;

import org.codeability.sharing.plugins.api.SharingPluginConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.service.SharingPluginService;

/**
 * REST controller for Supporting Import and Export from/to Sharing Platform.
 */
@Validated
@RestController
@RequestMapping("/api")
@Profile("sharing")
public class SharingSupportResource {

    private final Logger log = LoggerFactory.getLogger(SharingSupportResource.class);

    private final SharingPluginService sharingPluginService;

    public SharingSupportResource(SharingPluginService sharingPluginService) {
        this.sharingPluginService = sharingPluginService;
    }

    @Context
    UriInfo uri;

    /**
     * Returns Sharing Plugin configuration to be used in context with Artemis.
     *
     * @return Sharing Plugin configuration
     * @link https://sharing-codeability.uibk.ac.at/sharing/codeability-sharing-platform/-/wikis/technical/Connector-Interface
     *
     * @return Sharing Plugin configuration
     */
    @GetMapping(SHARINGCONFIG_RESOURCE_PATH)
    public ResponseEntity<SharingPluginConfig> getConfig(@RequestHeader("Authorization") Optional<String> sharingApiKey) {
        if (sharingPluginService.validate(sharingApiKey.get())) {
            log.debug("Received new Sharing Config request");
            return ResponseEntity.ok(sharingPluginService.getPluginConfig());
        }
        return ResponseEntity.status(401).body(null);
    }

    /**
     * Return a boolean value representing the current state of Sharing
     *
     * @return true if a Sharing ApiBaseUrl is present, false otherwise
     */
    @GetMapping(SHARINGCONFIG_RESOURCE_IS_ENABLED)
    public ResponseEntity<Boolean> isSharingEnabled() {
        return new ResponseEntity<>(sharingPluginService.isSharingApiBaseUrlPresent(), HttpStatus.OK);
    }
}
