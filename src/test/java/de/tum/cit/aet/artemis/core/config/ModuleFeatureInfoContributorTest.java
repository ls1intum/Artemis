package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.ACTIVE_MODULE_FEATURES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;
import org.springframework.core.env.Environment;

class ModuleFeatureInfoContributorTest {

    private final Environment mockEnv = mock(Environment.class);

    private static final List<String> modulePropertyNames = List.of(Constants.ATLAS_ENABLED_PROPERTY_NAME, Constants.PLAGIARISM_ENABLED_PROPERTY_NAME);

    @Test
    void testEnabledContribution() {
        testContribution(true, List.of(Constants.MODULE_FEATURE_ATLAS, Constants.MODULE_FEATURE_PLAGIARISM));
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

        var activeModuleFeaturesList = (List<String>) activeModuleFeatures;
        assertThat(activeModuleFeaturesList).containsExactlyInAnyOrderElementsOf(expectedReportFeatures);
    }

    private void mockProperty(String key, Boolean value) {
        when(mockEnv.getProperty(key, Boolean.class)).thenReturn(value);
    }
}
