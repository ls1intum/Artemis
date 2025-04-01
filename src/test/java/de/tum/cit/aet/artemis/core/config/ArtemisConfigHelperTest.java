package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

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

    private void mockProperty(String key, Boolean value) {
        when(mockEnv.getProperty(key, Boolean.class)).thenReturn(value);
    }
}
