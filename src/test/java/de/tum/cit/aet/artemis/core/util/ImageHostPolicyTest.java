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

    @Test
    void removesOnlyTheImagesOfOtherHosts() {
        var policy = ImageHostPolicy.of("images.example.org", SERVER_URL);
        var document = Jsoup
                .parseBodyFragment("<p>a <img src=\"https://images.example.org/a.png\"> b <img src=\"https://unlisted.example.net/b.png\"> c <img src=\"/api/files/c.png\"></p>");

        policy.removeDisallowedImages(document.body());

        assertThat(document.select("img").eachAttr("src")).containsExactly("https://images.example.org/a.png", "/api/files/c.png");
    }
}
