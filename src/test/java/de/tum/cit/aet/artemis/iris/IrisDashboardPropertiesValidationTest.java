package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.iris.config.IrisDashboardProperties;

class IrisDashboardPropertiesValidationTest {

    @Test
    void validDefaults_shouldNotThrow() {
        var props = new IrisDashboardProperties();
        assertThatNoException().isThrownBy(props::validate);
    }

    @Test
    void zeroStaleThreshold_shouldThrow() {
        var props = new IrisDashboardProperties();
        props.setStaleThresholdMinutes(0);
        assertThatThrownBy(props::validate).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zeroMaxQueryWindowDays_shouldThrow() {
        var props = new IrisDashboardProperties();
        props.setMaxQueryWindowDays(0);
        assertThatThrownBy(props::validate).isInstanceOf(IllegalArgumentException.class);
    }
}
