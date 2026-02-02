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
        Constants.MODELING_ENABLED_PROPERTY_NAME,
        Constants.FILEUPLOAD_ENABLED_PROPERTY_NAME,
        Constants.LECTURE_ENABLED_PROPERTY_NAME,
        Constants.TUTORIAL_GROUP_ENABLED_PROPERTY_NAME,
        Constants.PASSKEY_ENABLED_PROPERTY_NAME,
        Constants.NEBULA_ENABLED_PROPERTY_NAME,
        Constants.SHARING_ENABLED_PROPERTY_NAME,
        Constants.THEIA_ENABLED_PROPERTY_NAME,
        Constants.IRIS_ENABLED_PROPERTY_NAME,
        Constants.LTI_ENABLED_PROPERTY_NAME
    );
    // @formatter:on

    // @formatter:off
    private static final List<String> moduleFeatures = List.of(
        Constants.MODULE_FEATURE_ATLAS,
        Constants.MODULE_FEATURE_HYPERION,
        Constants.MODULE_FEATURE_EXAM,
        Constants.MODULE_FEATURE_PLAGIARISM,
        Constants.MODULE_FEATURE_TEXT,
        Constants.MODULE_FEATURE_MODELING,
        Constants.MODULE_FEATURE_FILEUPLOAD,
        Constants.MODULE_FEATURE_LECTURE,
        Constants.MODULE_FEATURE_TUTORIALGROUP,
        Constants.FEATURE_PASSKEY,
        Constants.MODULE_FEATURE_NEBULA,
        Constants.MODULE_FEATURE_SHARING,
        Constants.MODULE_FEATURE_THEIA,
        Constants.MODULE_FEATURE_IRIS,
        Constants.MODULE_FEATURE_LTI
    );
    // @formatter:on

    @Test
    void testEnabledContribution() {
        testContribution(true, false, moduleFeatures);
    }

    @Test
    void testDisabledContribution() {
        testContribution(false, false, List.of());
    }

    @Test
    void testPasskeyAdminEnabled() {
        for (String key : modulePropertyNames) {
            mockProperty(key, true);
        }
        mockProperty(Constants.PASSKEY_REQUIRE_FOR_ADMINISTRATOR_FEATURES_PROPERTY_NAME, true);

        ModuleFeatureInfoContributor contributor = new ModuleFeatureInfoContributor(mockEnv);

        Info.Builder builder = new Info.Builder();
        contributor.contribute(builder);
        Info info = builder.build();

        var activeModuleFeatures = info.get(ACTIVE_MODULE_FEATURES);
        assertThat(activeModuleFeatures).isInstanceOf(List.class);

        var activeModuleFeaturesList = (List<?>) activeModuleFeatures;
        var actualAsStrings = activeModuleFeaturesList.stream().map(Object::toString).toList();

        // Should include all features plus passkey-admin
        var expectedFeatures = new java.util.ArrayList<>(moduleFeatures);
        expectedFeatures.add(Constants.FEATURE_PASSKEY_REQUIRE_ADMIN);
        assertThat(actualAsStrings).containsExactlyInAnyOrderElementsOf(expectedFeatures);
    }

    private void testContribution(boolean propertyEnabled, boolean passkeyAdminRequired, List<String> expectedReportFeatures) {
        for (String key : modulePropertyNames) {
            mockProperty(key, propertyEnabled);
        }
        mockProperty(Constants.PASSKEY_REQUIRE_FOR_ADMINISTRATOR_FEATURES_PROPERTY_NAME, passkeyAdminRequired);

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
        // Also mock the 3-argument version with default value for properties like passkey-admin
        when(mockEnv.getProperty(key, Boolean.class, false)).thenReturn(value);
    }
}
