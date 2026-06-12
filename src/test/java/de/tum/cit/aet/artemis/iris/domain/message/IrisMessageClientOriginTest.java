package de.tum.cit.aet.artemis.iris.domain.message;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link IrisMessageClientOrigin#fromHeader(String)}. Since the resolved value comes from the untrusted
 * {@code X-Artemis-Client} HTTP header, the focus is on hardening: any missing, blank or unrecognized value must map to
 * {@link IrisMessageClientOrigin#UNKNOWN} rather than throw.
 */
class IrisMessageClientOriginTest {

    @ParameterizedTest
    @ValueSource(strings = { "ios", "IOS", "iOS", "  iOS  " })
    void fromHeader_resolvesIosCaseInsensitivelyAndTrimmed(String header) {
        assertThat(IrisMessageClientOrigin.fromHeader(header)).isEqualTo(IrisMessageClientOrigin.IOS);
    }

    @ParameterizedTest
    @ValueSource(strings = { "web", "WEB", "Web", " web " })
    void fromHeader_resolvesWebCaseInsensitivelyAndTrimmed(String header) {
        assertThat(IrisMessageClientOrigin.fromHeader(header)).isEqualTo(IrisMessageClientOrigin.WEB);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", "   ", "android", "ANDROID", "desktop", "ios-app", "42" })
    void fromHeader_returnsUnknownForMissingBlankOrUnrecognizedValues(String header) {
        assertThat(IrisMessageClientOrigin.fromHeader(header)).isEqualTo(IrisMessageClientOrigin.UNKNOWN);
    }

    @Test
    void fromHeader_resolvesUnknownLiteral() {
        assertThat(IrisMessageClientOrigin.fromHeader("unknown")).isEqualTo(IrisMessageClientOrigin.UNKNOWN);
    }
}
