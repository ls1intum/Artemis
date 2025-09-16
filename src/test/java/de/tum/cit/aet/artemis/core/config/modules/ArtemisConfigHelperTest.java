package de.tum.cit.aet.artemis.core.config.modules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

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
    void testNebulaProperty() {
        testProperty(artemisConfigHelper::isNebulaEnabled, Constants.NEBULA_ENABLED_PROPERTY_NAME);
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
    void testTutorialgroupProperty() {
        testProperty(artemisConfigHelper::isTutorialGroupEnabled, Constants.TUTORIAL_GROUP_ENABLED_PROPERTY_NAME);
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
