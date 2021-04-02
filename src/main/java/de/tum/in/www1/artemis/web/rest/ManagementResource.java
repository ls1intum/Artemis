package de.tum.in.www1.artemis.web.rest;

import java.util.*;

import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.service.feature.*;

@RestController
@RequestMapping(ManagementResource.ROOT_MANAGEMENT)
public class ManagementResource {

    static final String ROOT_MANAGEMENT = "/api/management";

    private static final String SUB_FEATURE_TOGGLE = "/feature-toggle";

    private final FeatureToggleService featureToggleService;

    public ManagementResource(FeatureToggleService featureToggleService) {
        this.featureToggleService = featureToggleService;
    }

    /**
     * PUT -- Updates all given features by enabling/disabling them. (Map of feature -> shouldBeEnabled)
     *
     * @see FeatureToggleService
     * @param features A map of features (feature -> shouldBeActivated)
     * @return A list of all enabled features
     */
    @PutMapping(SUB_FEATURE_TOGGLE)
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<List<Feature>> toggleFeatures(@RequestBody Map<Feature, Boolean> features) {
        featureToggleService.updateFeatureToggles(features);

        return new ResponseEntity<>(featureToggleService.enabledFeatures(), HttpStatus.OK);
    }
}
