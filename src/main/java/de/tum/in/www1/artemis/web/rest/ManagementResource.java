package de.tum.in.www1.artemis.web.rest;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureService;

@RestController
@RequestMapping(ManagementResource.ROOT_MANAGEMENT)
public class ManagementResource {

    static final String ROOT_MANAGEMENT = "/api/management";

    private static final String SUB_FEATURE_TOGGLE = "/feature-toggle";

    private final FeatureService featureService;

    public ManagementResource(FeatureService featureService) {
        this.featureService = featureService;
    }

    /**
     * PUT -- Updates all given features by enabling/disabling them. (Map of feature -> shouldBeEnabled)
     *
     * @see FeatureService
     * @param features A map of features (feature -> shouldBeActivated)
     * @return A list of all enabled features
     */
    @PutMapping(SUB_FEATURE_TOGGLE)
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<List<Feature>> toggleFeatures(@RequestBody Map<Feature, Boolean> features) {
        featureService.updateFeatureToggles(features);

        return new ResponseEntity<>(featureService.enabledFeatures(), HttpStatus.OK);
    }
}
