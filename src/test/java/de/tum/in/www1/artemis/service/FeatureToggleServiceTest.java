package de.tum.in.www1.artemis.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggleService;

class FeatureToggleServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private FeatureToggleService featureToggleService;

    @AfterEach
    void checkReset() {
        // Verify that the test has reset the state
        // Must be extended if additional features are added
        assertTrue(featureToggleService.isFeatureEnabled(Feature.ProgrammingExercises));
    }

    @Test
    void testSetFeaturesEnabled() {
        Map<Feature, Boolean> featureStates = new HashMap<>();
        featureStates.put(Feature.ProgrammingExercises, true);
        featureToggleService.updateFeatureToggles(featureStates);
        assertTrue(featureToggleService.isFeatureEnabled(Feature.ProgrammingExercises));
    }

    @Test
    void testSetFeaturesDisabled() {
        assertTrue(featureToggleService.isFeatureEnabled(Feature.ProgrammingExercises));

        Map<Feature, Boolean> featureStates = new HashMap<>();
        featureStates.put(Feature.ProgrammingExercises, false);
        featureToggleService.updateFeatureToggles(featureStates);
        assertFalse(featureToggleService.isFeatureEnabled(Feature.ProgrammingExercises));

        // Reset
        featureToggleService.enableFeature(Feature.ProgrammingExercises);
    }

    @Test
    void testEnableDisableFeature() {
        assertTrue(featureToggleService.isFeatureEnabled(Feature.ProgrammingExercises));

        featureToggleService.disableFeature(Feature.ProgrammingExercises);
        assertFalse(featureToggleService.isFeatureEnabled(Feature.ProgrammingExercises));

        featureToggleService.enableFeature(Feature.ProgrammingExercises);
        assertTrue(featureToggleService.isFeatureEnabled(Feature.ProgrammingExercises));
    }

    @Test
    void testShouldNotEnableTwice() {
        assertEquals(Feature.values().length, featureToggleService.enabledFeatures().size());
        featureToggleService.enableFeature(Feature.ProgrammingExercises);

        // Feature should not be added multiple times
        assertEquals(Feature.values().length, featureToggleService.enabledFeatures().size());
    }

    @Test
    void testShouldNotDisableTwice() {
        featureToggleService.disableFeature(Feature.ProgrammingExercises);

        assertEquals(1, featureToggleService.disabledFeatures().size());
        featureToggleService.disableFeature(Feature.ProgrammingExercises);

        // Feature should not be added multiple times
        assertEquals(1, featureToggleService.disabledFeatures().size());

        // Reset
        featureToggleService.enableFeature(Feature.ProgrammingExercises);
    }
}
