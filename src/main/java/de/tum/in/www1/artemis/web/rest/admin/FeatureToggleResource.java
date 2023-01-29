package de.tum.in.www1.artemis.web.rest.admin;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.security.annotations.EnforceAdmin;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggleService;

@RestController
@RequestMapping("api/admin/")
public class FeatureToggleResource {

    private final FeatureToggleService featureToggleService;

    public FeatureToggleResource(FeatureToggleService featureToggleService) {
        this.featureToggleService = featureToggleService;
    }

    /**
     * PUT feature-toggle -- Updates all given features by enabling/disabling them. (Map of feature -> shouldBeEnabled)
     *
     * @see FeatureToggleService
     * @param features A map of features (feature -> shouldBeActivated)
     * @return A list of all enabled features
     */
    @PutMapping("feature-toggle")
    @EnforceAdmin
    public ResponseEntity<List<Feature>> toggleFeatures(@RequestBody Map<Feature, Boolean> features) {
        featureToggleService.updateFeatureToggles(features);

        return new ResponseEntity<>(featureToggleService.enabledFeatures(), HttpStatus.OK);
    }
}
