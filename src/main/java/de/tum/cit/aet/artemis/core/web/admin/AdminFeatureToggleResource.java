package de.tum.cit.aet.artemis.core.web.admin;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;

@Profile(PROFILE_CORE)
@EnforceAdmin
@RestController
@RequestMapping("api/admin/")
public class AdminFeatureToggleResource {

    private final FeatureToggleService featureToggleService;

    public AdminFeatureToggleResource(FeatureToggleService featureToggleService) {
        this.featureToggleService = featureToggleService;
    }

    /**
     * PUT feature-toggle -- Updates all given features by enabling/disabling them. (Map of feature -> shouldBeEnabled)
     *
     * @param features A map of features (feature -> shouldBeActivated)
     * @return A list of all enabled features
     * @see FeatureToggleService
     */
    @PutMapping("feature-toggle")
    public ResponseEntity<List<Feature>> toggleFeatures(@RequestBody Map<Feature, Boolean> features) {
        featureToggleService.updateFeatureToggles(features);

        return new ResponseEntity<>(featureToggleService.enabledFeatures(), HttpStatus.OK);
    }
}
