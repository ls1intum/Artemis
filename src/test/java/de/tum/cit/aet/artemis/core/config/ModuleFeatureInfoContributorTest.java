package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.ACTIVE_MODULE_FEATURES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;
import org.springframework.core.env.Environment;

/**
 * Test for {@link ModuleFeatureInfoContributor}.
 */
class ModuleFeatureInfoContributorTest {

    private final Environment mockEnv = mock(Environment.class);

    // @formatter:off
    private static final List<String> modulePropertyNames = List.of(
        Constants.ATLAS_ENABLED_PROPERTY_NAME,
        Constants.HYPERION_ENABLED_PROPERTY_NAME,
        Constants.EXAM_ENABLED_PROPERTY_NAME,
        Constants.PLAGIARISM_ENABLED_PROPERTY_NAME,
        Constants.TEXT_ENABLED_PROPERTY_NAME,
        Constants.TUTORIAL_GROUP_ENABLED_PROPERTY_NAME,
        Constants.PASSKEY_ENABLED_PROPERTY_NAME,
        Constants.NEBULA_ENABLED_PROPERTY_NAME
    );
    // @formatter:on

    // @formatter:off
    private static final List<String> moduleFeatures = List.of(
        Constants.MODULE_FEATURE_ATLAS,
        Constants.MODULE_FEATURE_HYPERION,
        Constants.MODULE_FEATURE_EXAM,
        Constants.MODULE_FEATURE_PLAGIARISM,
        Constants.MODULE_FEATURE_TEXT,
        Constants.MODULE_FEATURE_TUTORIALGROUP,
        Constants.FEATURE_PASSKEY,
        Constants.MODULE_FEATURE_NEBULA
    );
    // @formatter:on

    @Test
    void testEnabledContribution() {
        testContribution(true, moduleFeatures);
    }

    @Test
    void testDisabledContribution() {
        testContribution(false, List.of());
    }

    private void testContribution(boolean propertyEnabled, List<String> expectedReportFeatures) {
        for (String key : modulePropertyNames) {
            mockProperty(key, propertyEnabled);
        }

        ModuleFeatureInfoContributor contributor = new ModuleFeatureInfoContributor(mockEnv);

        Info.Builder builder = new Info.Builder();
        contributor.contribute(builder);
        Info info = builder.build();

        var activeModuleFeatures = info.get(ACTIVE_MODULE_FEATURES);
        assertThat(activeModuleFeatures).isInstanceOf(List.class);

        var activeModuleFeaturesList = (List<?>) activeModuleFeatures;
        var actualAsStrings = activeModuleFeaturesList.stream().map(Object::toString).toList();
        assertThat(actualAsStrings).containsExactlyInAnyOrderElementsOf(expectedReportFeatures);
    }

    private void mockProperty(String key, Boolean value) {
        when(mockEnv.getProperty(key, Boolean.class)).thenReturn(value);
    }
}
