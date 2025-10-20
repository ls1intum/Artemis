package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.core.config.RateLimitingProperties;
import de.tum.cit.aet.artemis.core.security.RateLimitType;

@ExtendWith(MockitoExtension.class)
class RateLimitConfigurationServiceTest {

    @Mock
    private RateLimitingProperties properties;

    private RateLimitConfigurationService configurationService;

    @BeforeEach
    void setUp() {
        configurationService = new RateLimitConfigurationService(properties);
    }

    @Test
    void testIsRateLimitingEnabled_WhenEnabled_ShouldReturnTrue() {
        when(properties.isEnabled()).thenReturn(true);

        assertThat(configurationService.isRateLimitingEnabled()).isTrue();
    }

    @Test
    void testIsRateLimitingEnabled_WhenDisabled_ShouldReturnFalse() {
        when(properties.isEnabled()).thenReturn(false);

        assertThat(configurationService.isRateLimitingEnabled()).isFalse();
    }

    @Test
    void testGetEffectiveRpm_PublicType_WithCustomValue_ShouldReturnCustomValue() {
        when(properties.getPublicRpm()).thenReturn(10);

        int rpm = configurationService.getEffectiveRpm(RateLimitType.PUBLIC);

        assertThat(rpm).isEqualTo(10);
    }

    @Test
    void testGetEffectiveRpm_PublicType_WithNullValue_ShouldReturnDefault() {
        when(properties.getPublicRpm()).thenReturn(null);

        int rpm = configurationService.getEffectiveRpm(RateLimitType.PUBLIC);

        assertThat(rpm).isEqualTo(RateLimitType.PUBLIC.getDefaultRpm()); // 5
    }

    @Test
    void testGetEffectiveRpm_LoginRelatedType_WithCustomValue_ShouldReturnCustomValue() {
        when(properties.getLoginRelatedRpm()).thenReturn(50);

        int rpm = configurationService.getEffectiveRpm(RateLimitType.LOGIN_RELATED);

        assertThat(rpm).isEqualTo(50);
    }

    @Test
    void testGetEffectiveRpm_LoginRelatedType_WithNullValue_ShouldReturnDefault() {
        when(properties.getLoginRelatedRpm()).thenReturn(null);

        int rpm = configurationService.getEffectiveRpm(RateLimitType.LOGIN_RELATED);

        assertThat(rpm).isEqualTo(RateLimitType.LOGIN_RELATED.getDefaultRpm()); // 30
    }

    @Test
    void testGetEffectiveRpm_FixedValue_ShouldReturnSameValue() {
        int rpm = configurationService.getEffectiveRpm(15);

        assertThat(rpm).isEqualTo(15);
    }

    @Test
    void testGetEffectiveRpm_FixedValueZero_ShouldReturnZero() {
        int rpm = configurationService.getEffectiveRpm(0);

        assertThat(rpm).isEqualTo(0);
    }
}
