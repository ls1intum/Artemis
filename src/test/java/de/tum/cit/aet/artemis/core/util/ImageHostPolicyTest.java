package de.tum.cit.aet.artemis.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ImageHostPolicyTest {

    private static final String SERVER_URL = "https://artemis.example.org";

    @ParameterizedTest
    @ValueSource(strings = { "https://anywhere.example.net/a.png", "http://other.example.com/a.png", "//cdn.example.net/a.png", "relative/a.png", "/api/files/a.png" })
    void everyHostIsAllowedWhileNoHostIsConfigured(String src) {
        var policy = ImageHostPolicy.of("", SERVER_URL);

        assertThat(policy.isOpen()).isTrue();
        assertThat(policy.allows(src)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://images.example.org/a.png", "HTTPS://Images.Example.org:8443/a.png", "https://artemis.example.org/files/a.png", "//images.example.org/a.png",
            "https://eu.cdn.example.net/a.png", "relative/a.png", "/api/files/a.png", "data:image/png;base64,AAAA" })
    void allowsConfiguredHostsTheOwnHostRelativeAndInlineAddresses(String src) {
        var policy = ImageHostPolicy.of("images.example.org, *.cdn.example.net", SERVER_URL);

        assertThat(policy.allows(src)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://unlisted.example.net/a.png", "http://example.org/a.png", "https://cdn.example.net/a.png", "https://images.example.org.unlisted.example/a.png",
            "https://user@unlisted.example.net/a.png", "//unlisted.example.net/a.png", "ftp://images.example.org/a.png" })
    void refusesOtherHosts(String src) {
        var policy = ImageHostPolicy.of("images.example.org, *.cdn.example.net", SERVER_URL);

        assertThat(policy.allows(src)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://xn--bcher-kva.example/a.png", "https://bücher.example/a.png", "https://B\u00DCCHER.example/a.png", "https://bücher.example:8443/a.png",
            "https://user@bücher.example/a.png", "//bücher.example/a.png", "https://shop.bücher.example/a.png" })
    void comparesHostsWithSpecialCharactersInTheirAsciiForm(String src) {
        var policy = ImageHostPolicy.of("bücher.example, *.bücher.example", SERVER_URL);

        assertThat(policy.allows(src)).isTrue();
    }

    @Test
    void aConfiguredHostInAsciiFormMatchesTheSpecialCharactersOfAnAddress() {
        var policy = ImageHostPolicy.of("xn--bcher-kva.example, *.xn--bcher-kva.example", SERVER_URL);

        assertThat(policy.allows("https://bücher.example/a.png")).isTrue();
        assertThat(policy.allows("https://shop.bücher.example/a.png")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://bücher.example@unlisted.example/a.png", "https://bücher.example.unlisted.example/a.png", "https://büchers.example/a.png",
            "https://xn--bcher-kva.example.unlisted.example/a.png", "https://[::1]/a.png" })
    void doesNotTakeAnotherHostForAHostWithSpecialCharacters(String src) {
        var policy = ImageHostPolicy.of("bücher.example", SERVER_URL);

        assertThat(policy.allows(src)).isFalse();
    }

    @Test
    void keepsTheLetterThatTheOlderRulesOfJavaFoldIntoAnotherDomain() {
        // a browser keeps the sharp s, so faß.example is xn--fa-hia.example, and fass.example is another domain
        var policy = ImageHostPolicy.of("faß.example", SERVER_URL);

        assertThat(policy.allows("https://xn--fa-hia.example/a.png")).isTrue();
        assertThat(policy.allows("https://faß.example/a.png")).isTrue();
        assertThat(policy.allows("https://FAß.EXAMPLE/a.png")).isTrue();
        assertThat(policy.allows("https://fass.example/a.png")).isFalse();
    }

    @Test
    void doesNotMatchTheSharpSOfAnImageToAnEntryWithTwoLetters() {
        var policy = ImageHostPolicy.of("fass.example", SERVER_URL);

        assertThat(policy.allows("https://fass.example/a.png")).isTrue();
        assertThat(policy.allows("https://faß.example/a.png")).isFalse();
        assertThat(policy.allows("https://xn--fa-hia.example/a.png")).isFalse();
    }

    @Test
    void keepsTheFinalSigmaOfAGreekName() {
        // the nontransitional form of βόλος.example ends in the final sigma, the transitional one in the ordinary sigma
        var policy = ImageHostPolicy.of("βόλος.example", SERVER_URL);

        assertThat(policy.allows("https://xn--nxasmm1c.example/a.png")).isTrue();
        assertThat(policy.allows("https://βόλος.example/a.png")).isTrue();
        assertThat(policy.allows("https://xn--nxasmq6b.example/a.png")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://xn--/a.png", "https://xn--zz-/a.png", "https://a..example/a.png" })
    void refusesAHostThatCannotBeNormalized(String src) {
        var policy = ImageHostPolicy.of("images.example.org", SERVER_URL);

        assertThat(policy.allows(src)).isFalse();
    }

    @Test
    void keepsAnIpv6AddressAsItIs() {
        var policy = ImageHostPolicy.of("[::1]", SERVER_URL);

        assertThat(policy.allows("https://[::1]/a.png")).isTrue();
        assertThat(policy.allows("https://[::2]/a.png")).isFalse();
    }

    @Test
    void anEntryThatIsNoPlainHostNameStaysAsItIsWritten() {
        var policy = ImageHostPolicy.of("images.example.net:8443, images.example.org/path", SERVER_URL);

        assertThat(policy.allowedHosts()).containsExactly("images.example.net:8443", "images.example.org/path");
        assertThat(policy.allows("https://images.example.net/a.png")).isFalse();
        assertThat(policy.allows("https://images.example.org/a.png")).isFalse();
    }

    @Test
    void removesOnlyTheImagesOfOtherHosts() {
        var policy = ImageHostPolicy.of("images.example.org", SERVER_URL);
        var document = Jsoup
                .parseBodyFragment("<p>a <img src=\"https://images.example.org/a.png\"> b <img src=\"https://unlisted.example.net/b.png\"> c <img src=\"/api/files/c.png\"></p>");

        policy.removeDisallowedImages(document.body());

        assertThat(document.select("img").eachAttr("src")).containsExactly("https://images.example.org/a.png", "/api/files/c.png");
    }
}
