package de.tum.cit.aet.artemis.core.config.modules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mock.env.MockEnvironment;

import de.tum.cit.aet.artemis.core.config.ArtemisConfigHelper;
import de.tum.cit.aet.artemis.core.config.Constants;

/**
 * Tests for {@link ArtemisConfigHelper}.
 */
class ArtemisConfigHelperTest {

    private final ArtemisConfigHelper artemisConfigHelper = new ArtemisConfigHelper();

    private final Environment mockEnv = mock(Environment.class);

    @Test
    void testAtlasProperty() {
        testProperty(artemisConfigHelper::isAtlasEnabled, Constants.ATLAS_ENABLED_PROPERTY_NAME);
    }

    @Test
    void testAtlasMLProperty() {
        mockProperty(Constants.ATLAS_ENABLED_PROPERTY_NAME, true);

        mockProperty(Constants.ATLASML_ENABLED_PROPERTY_NAME, true);
        assertThat(artemisConfigHelper.isAtlasMLEnabled(mockEnv)).isTrue();

        mockProperty(Constants.ATLASML_ENABLED_PROPERTY_NAME, false);
        assertThat(artemisConfigHelper.isAtlasMLEnabled(mockEnv)).isFalse();

        mockProperty(Constants.ATLASML_ENABLED_PROPERTY_NAME, null);
        assertThatThrownBy(() -> artemisConfigHelper.isAtlasMLEnabled(mockEnv)).isInstanceOf(RuntimeException.class).hasMessageContaining(Constants.ATLASML_ENABLED_PROPERTY_NAME);

        mockProperty(Constants.ATLAS_ENABLED_PROPERTY_NAME, false);
        mockProperty(Constants.ATLASML_ENABLED_PROPERTY_NAME, true);
        assertThat(artemisConfigHelper.isAtlasMLEnabled(mockEnv)).isFalse();
    }

    /**
     * The last case is the one that matters in production: a build agent runs with the Atlas module off and never
     * defines the AtlasLLM property, so the short-circuit on the module flag is what keeps it from throwing.
     */
    @Test
    void testAtlasLLMProperty() {
        mockProperty(Constants.ATLAS_ENABLED_PROPERTY_NAME, true);

        mockProperty(Constants.ATLASLLM_ENABLED_PROPERTY_NAME, true);
        assertThat(artemisConfigHelper.isAtlasLLMEnabled(mockEnv)).isTrue();

        mockProperty(Constants.ATLASLLM_ENABLED_PROPERTY_NAME, false);
        assertThat(artemisConfigHelper.isAtlasLLMEnabled(mockEnv)).isFalse();

        mockProperty(Constants.ATLASLLM_ENABLED_PROPERTY_NAME, null);
        assertThatThrownBy(() -> artemisConfigHelper.isAtlasLLMEnabled(mockEnv)).isInstanceOf(RuntimeException.class)
                .hasMessageContaining(Constants.ATLASLLM_ENABLED_PROPERTY_NAME);

        mockProperty(Constants.ATLAS_ENABLED_PROPERTY_NAME, false);
        mockProperty(Constants.ATLASLLM_ENABLED_PROPERTY_NAME, null);
        assertThat(artemisConfigHelper.isAtlasLLMEnabled(mockEnv)).isFalse();
    }

    @Test
    void testExamProperty() {
        testProperty(artemisConfigHelper::isExamEnabled, Constants.EXAM_ENABLED_PROPERTY_NAME);
    }

    @Test
    void testPlagiarismProperty() {
        testProperty(artemisConfigHelper::isPlagiarismEnabled, Constants.PLAGIARISM_ENABLED_PROPERTY_NAME);
    }

    @Test
    void testTextExerciseProperty() {
        testProperty(artemisConfigHelper::isTextExerciseEnabled, Constants.TEXT_ENABLED_PROPERTY_NAME);
    }

    @Test
    void testModelingProperty() {
        testProperty(artemisConfigHelper::isModelingEnabled, Constants.MODELING_ENABLED_PROPERTY_NAME);
    }

    @Test
    void testFileUploadProperty() {
        testProperty(artemisConfigHelper::isFileUploadEnabled, Constants.FILEUPLOAD_ENABLED_PROPERTY_NAME);
    }

    @Test
    void testLectureProperty() {
        testProperty(artemisConfigHelper::isLectureEnabled, Constants.LECTURE_ENABLED_PROPERTY_NAME);
    }

    @Test
    void testTutorialgroupProperty() {
        testProperty(artemisConfigHelper::isTutorialGroupEnabled, Constants.TUTORIAL_GROUP_ENABLED_PROPERTY_NAME);
    }

    @Test
    void testIrisProperty() {
        testProperty(artemisConfigHelper::isIrisEnabled, Constants.IRIS_ENABLED_PROPERTY_NAME);
    }

    @Test
    void testDeimosProperty() {
        testProperty(artemisConfigHelper::isDeimosEnabled, Constants.DEIMOS_ENABLED_PROPERTY_NAME);
    }

    @Test
    void testMetisProperty() {
        testProperty(artemisConfigHelper::isMetisEnabled, Constants.METIS_ENABLED_PROPERTY_NAME);
    }

    @Test
    void testHyperionExerciseGenerationRequiresHyperionPropertyAndProfiles() {
        mockProperty(Constants.HYPERION_ENABLED_PROPERTY_NAME, true);
        when(mockEnv.getProperty(Constants.HYPERION_EXERCISE_GENERATION_ENABLED_PROPERTY_NAME, Boolean.class, false)).thenReturn(true);
        when(mockEnv.acceptsProfiles(any(Profiles.class))).thenReturn(true);
        assertThat(artemisConfigHelper.isHyperionExerciseGenerationEnabled(mockEnv)).isTrue();

        when(mockEnv.acceptsProfiles(any(Profiles.class))).thenReturn(false);
        assertThat(artemisConfigHelper.isHyperionExerciseGenerationEnabled(mockEnv)).isFalse();

        when(mockEnv.acceptsProfiles(any(Profiles.class))).thenReturn(true);
        when(mockEnv.getProperty(Constants.HYPERION_EXERCISE_GENERATION_ENABLED_PROPERTY_NAME, Boolean.class, false)).thenReturn(false);
        assertThat(artemisConfigHelper.isHyperionExerciseGenerationEnabled(mockEnv)).isFalse();

        when(mockEnv.getProperty(Constants.HYPERION_EXERCISE_GENERATION_ENABLED_PROPERTY_NAME, Boolean.class, false)).thenReturn(true);
        mockProperty(Constants.HYPERION_ENABLED_PROPERTY_NAME, false);
        assertThat(artemisConfigHelper.isHyperionExerciseGenerationEnabled(mockEnv)).isFalse();
    }

    @Test
    void testAiWorkerRunsOnCoreForGenerationOrExplicitOptIn() {
        mockProperty(Constants.HYPERION_ENABLED_PROPERTY_NAME, false);
        when(mockEnv.getProperty(Constants.HYPERION_ENABLED_PROPERTY_NAME, Boolean.class, false)).thenReturn(false);
        when(mockEnv.acceptsProfiles(any(Profiles.class))).thenReturn(true);
        when(mockEnv.getProperty(Constants.AIWORKER_ENABLED_PROPERTY_NAME, Boolean.class, false)).thenReturn(true);
        assertThat(artemisConfigHelper.isAiWorkerEnabled(mockEnv)).isTrue();

        when(mockEnv.acceptsProfiles(any(Profiles.class))).thenReturn(false);
        assertThat(artemisConfigHelper.isAiWorkerEnabled(mockEnv)).isFalse();

        when(mockEnv.acceptsProfiles(any(Profiles.class))).thenReturn(true);
        when(mockEnv.getProperty(Constants.AIWORKER_ENABLED_PROPERTY_NAME, Boolean.class, false)).thenReturn(false);
        assertThat(artemisConfigHelper.isAiWorkerEnabled(mockEnv)).isFalse();

        mockProperty(Constants.HYPERION_ENABLED_PROPERTY_NAME, true);
        when(mockEnv.getProperty(Constants.HYPERION_ENABLED_PROPERTY_NAME, Boolean.class, false)).thenReturn(true);
        when(mockEnv.getProperty(Constants.HYPERION_EXERCISE_GENERATION_ENABLED_PROPERTY_NAME, Boolean.class, false)).thenReturn(true);
        assertThat(artemisConfigHelper.isAiWorkerEnabled(mockEnv)).isTrue();

        when(mockEnv.getProperty(Constants.HYPERION_EXERCISE_GENERATION_ENABLED_PROPERTY_NAME, Boolean.class, false)).thenReturn(false);
        assertThat(artemisConfigHelper.isAiWorkerEnabled(mockEnv)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "aiworker", "prod,aiworker" })
    void workerDoesNotLoadServerModulesEvenWithInheritedFeatureFlags(String profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles.split(","));
        assertThat(artemisConfigHelper.getEnabledFeatures(environment)).isEmpty();
        for (String property : List.of(Constants.ATLAS_ENABLED_PROPERTY_NAME, Constants.ATLASML_ENABLED_PROPERTY_NAME, Constants.ATLASLLM_ENABLED_PROPERTY_NAME,
                Constants.HYPERION_ENABLED_PROPERTY_NAME, Constants.HYPERION_EXERCISE_GENERATION_ENABLED_PROPERTY_NAME, Constants.DEIMOS_ENABLED_PROPERTY_NAME,
                Constants.METIS_ENABLED_PROPERTY_NAME, Constants.IRIS_ENABLED_PROPERTY_NAME, Constants.EXAM_ENABLED_PROPERTY_NAME, Constants.PLAGIARISM_ENABLED_PROPERTY_NAME,
                Constants.TEXT_ENABLED_PROPERTY_NAME, Constants.MODELING_ENABLED_PROPERTY_NAME, Constants.FILEUPLOAD_ENABLED_PROPERTY_NAME, Constants.LECTURE_ENABLED_PROPERTY_NAME,
                Constants.TUTORIAL_GROUP_ENABLED_PROPERTY_NAME, Constants.PASSKEY_ENABLED_PROPERTY_NAME, Constants.SHARING_ENABLED_PROPERTY_NAME,
                Constants.LTI_ENABLED_PROPERTY_NAME, Constants.ATHENA_ENABLED_PROPERTY_NAME, Constants.APOLLON_ENABLED_PROPERTY_NAME, Constants.LDAP_ENABLED_PROPERTY_NAME,
                Constants.SAML2_ENABLED_PROPERTY_NAME, Constants.OIDC_ENABLED_PROPERTY_NAME, Constants.THEIA_ENABLED_PROPERTY_NAME, Constants.WEAVIATE_ENABLED_PROPERTY_NAME)) {
            environment.setProperty(property, "true");
        }
        assertThat(artemisConfigHelper.getEnabledFeatures(environment)).isEmpty();
        assertThat(artemisConfigHelper.isOIDCEnabled(environment)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "core", "buildagent", "prod,core", "aiworker,core" })
    void serverAndBuildAgentStillRequireExplicitModuleConfiguration(String profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles.split(","));
        assertThatThrownBy(() -> artemisConfigHelper.isAtlasEnabled(environment)).isInstanceOf(RuntimeException.class).hasMessageContaining(Constants.ATLAS_ENABLED_PROPERTY_NAME);
        environment.setProperty(Constants.ATLAS_ENABLED_PROPERTY_NAME, "true");
        assertThat(artemisConfigHelper.isAtlasEnabled(environment)).isTrue();
        environment.setProperty(Constants.ATLAS_ENABLED_PROPERTY_NAME, "false");
        assertThat(artemisConfigHelper.isAtlasEnabled(environment)).isFalse();
    }

    private void testProperty(Function<Environment, Boolean> propertyTest, String propertyName) {
        mockProperty(propertyName, true);
        assertThat(propertyTest.apply(mockEnv)).isTrue();

        mockProperty(propertyName, false);
        assertThat(propertyTest.apply(mockEnv)).isFalse();

        mockProperty(propertyName, null);
        assertThatThrownBy(() -> propertyTest.apply(mockEnv)).isInstanceOf(RuntimeException.class).hasMessageContaining(propertyName);
    }

    private void mockProperty(String key, Boolean value) {
        when(mockEnv.getProperty(key, Boolean.class)).thenReturn(value);
    }
}
