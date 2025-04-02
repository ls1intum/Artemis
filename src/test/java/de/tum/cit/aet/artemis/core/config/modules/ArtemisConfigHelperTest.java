package de.tum.cit.aet.artemis.core.config.modules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import de.tum.cit.aet.artemis.core.config.ArtemisConfigHelper;
import de.tum.cit.aet.artemis.core.config.Constants;

class ArtemisConfigHelperTest {

    private final ArtemisConfigHelper artemisConfigHelper = new ArtemisConfigHelper();

    private final Environment mockEnv = mock(Environment.class);

    @Test
    void testAtlasProperty() {
        mockProperty(Constants.ATLAS_ENABLED_PROPERTY_NAME, true);
        assertThat(artemisConfigHelper.isAtlasEnabled(mockEnv)).isTrue();

        mockProperty(Constants.ATLAS_ENABLED_PROPERTY_NAME, false);
        assertThat(artemisConfigHelper.isAtlasEnabled(mockEnv)).isFalse();

        mockProperty(Constants.ATLAS_ENABLED_PROPERTY_NAME, null);
        assertThatThrownBy(() -> artemisConfigHelper.isAtlasEnabled(mockEnv)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void testPlagiarismProperty() {
        mockProperty(Constants.PLAGIARISM_ENABLED_PROPERTY_NAME, true);
        assertThat(artemisConfigHelper.isPlagiarismEnabled(mockEnv)).isTrue();

        mockProperty(Constants.PLAGIARISM_ENABLED_PROPERTY_NAME, false);
        assertThat(artemisConfigHelper.isPlagiarismEnabled(mockEnv)).isFalse();

        mockProperty(Constants.PLAGIARISM_ENABLED_PROPERTY_NAME, null);
        assertThatThrownBy(() -> artemisConfigHelper.isPlagiarismEnabled(mockEnv)).isInstanceOf(RuntimeException.class);
    }

    private void mockProperty(String key, Boolean value) {
        when(mockEnv.getProperty(key, Boolean.class)).thenReturn(value);
    }
}
