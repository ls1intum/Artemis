package de.tum.cit.aet.artemis.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class SharingChecksumTest {

    @Test
    void matchesThePublishedChecksumAndVerifiesItExactly() {
        var parameters = Map.of("returnURL", "https://sharing.uibk.ac.at", "apiBaseURL", "https://sharing.uibk.ac.at/api");
        String expected = "b67abbc4593c040262df569c89ab5bf0";

        assertThat(SharingChecksum.calculateChecksum(parameters, "abcdefg")).isEqualTo(expected);
        assertThat(SharingChecksum.checkChecksum(parameters, "abcdefg", expected)).isTrue();
        assertThat(SharingChecksum.checkChecksum(parameters, "different secret", expected)).isFalse();
        assertThat(SharingChecksum.checkChecksum(parameters, "abcdefg", expected.toUpperCase(Locale.ROOT))).isFalse();
        assertThat(SharingChecksum.checkChecksum(parameters, "abcdefg", null)).isFalse();
    }

    @Test
    void ordersValuesByParameterNameAndUsesTheSameCharsetAsThePlatform() {
        Charset charset = Charset.defaultCharset();
        Assumptions.assumeTrue(charset.equals(StandardCharsets.UTF_8) || charset.equals(StandardCharsets.US_ASCII));
        var parameters = new LinkedHashMap<String, String>();
        parameters.put("z space", "a+b/🐍");
        parameters.put("a", "Grüße & +");
        // Both values were generated with SharingPluginPlatformAPI 1.2.1.
        String expected = charset.equals(StandardCharsets.UTF_8) ? "13d225e4bbed53da9ee45730eb940686" : "5a205f2f05a7f049a626622e3f29bf9e";

        assertThat(SharingChecksum.calculateChecksum(parameters, "sëcret")).isEqualTo(expected);
        assertThat(SharingChecksum.calculateChecksum(parameters.reversed(), "sëcret")).isEqualTo(expected);
    }
}
