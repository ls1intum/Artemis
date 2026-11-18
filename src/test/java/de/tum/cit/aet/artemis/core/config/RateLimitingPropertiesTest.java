package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RateLimitingPropertiesTest {

    @Test
    void rateLimitingIsEnabledUnlessAnOperatorSwitchesItOff() {
        assertThat(new RateLimitingProperties().isEnabled()).isTrue();
    }
}
