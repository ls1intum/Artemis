package de.tum.cit.aet.artemis.iris.domain.settings;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IrisSupportLevelTest {

    @Test
    void fromJson_parsesKnownValues() {
        assertThat(IrisSupportLevel.fromJson("low")).isEqualTo(IrisSupportLevel.LOW);
        assertThat(IrisSupportLevel.fromJson("moderate")).isEqualTo(IrisSupportLevel.MODERATE);
        assertThat(IrisSupportLevel.fromJson("high")).isEqualTo(IrisSupportLevel.HIGH);
    }

    @Test
    void fromJson_isCaseInsensitive() {
        assertThat(IrisSupportLevel.fromJson("HIGH")).isEqualTo(IrisSupportLevel.HIGH);
        assertThat(IrisSupportLevel.fromJson("Low")).isEqualTo(IrisSupportLevel.LOW);
    }

    @Test
    void fromJson_defaultsToModerateForNull() {
        assertThat(IrisSupportLevel.fromJson(null)).isEqualTo(IrisSupportLevel.MODERATE);
    }

    @Test
    void fromJson_defaultsToModerateForUnknown() {
        assertThat(IrisSupportLevel.fromJson("unknown")).isEqualTo(IrisSupportLevel.MODERATE);
    }

    @Test
    void jsonValue_returnsLowercaseString() {
        assertThat(IrisSupportLevel.LOW.jsonValue()).isEqualTo("low");
        assertThat(IrisSupportLevel.MODERATE.jsonValue()).isEqualTo("moderate");
        assertThat(IrisSupportLevel.HIGH.jsonValue()).isEqualTo("high");
    }
}
