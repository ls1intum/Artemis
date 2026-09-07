package de.tum.cit.aet.artemis.communication.service.linkpreview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class LinkPreviewUrlValidatorTest {

    @Test
    void retainsOnlyPublicIpv4ResolvedAddresses() throws Exception {
        InetAddress[] addresses = { InetAddress.getByName("93.184.216.34"), InetAddress.getByName("2606:4700:4700::1111") };
        var validator = new LinkPreviewUrlValidator(host -> addresses);
        URI uri = URI.create("https://example.com/page");

        var result = validator.validateAndResolve(uri);

        assertThat(result.uri()).isEqualTo(uri);
        assertThat(result.addresses()).containsExactly(addresses[0]);
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://localhost", "http://127.0.0.1", "http://[::1]", "ftp://example.com", "javascript:void(0)", "https://user@example.com",
            "https://example.superlongtldover20chars", "https://example.com:99999", "example.com" })
    void rejectsInvalidUrls(String url) {
        var validator = new LinkPreviewUrlValidator(host -> new InetAddress[] { InetAddress.getByName("93.184.216.34") });

        assertThatThrownBy(() -> validator.validateAndResolve(URI.create(url))).isInstanceOf(IOException.class);
    }

    @ParameterizedTest
    @MethodSource("unsupportedAddresses")
    void rejectsNonPublicIpv4AndIpv6OnlyResults(String address) throws Exception {
        var validator = new LinkPreviewUrlValidator(host -> new InetAddress[] { InetAddress.getByName(address) });

        assertThatThrownBy(() -> validator.validateAndResolve(URI.create("https://example.com"))).isInstanceOf(UnknownHostException.class);
    }

    @Test
    void rejectsResultContainingNonPublicAddress() throws Exception {
        var validator = new LinkPreviewUrlValidator(host -> new InetAddress[] { InetAddress.getByName("93.184.216.34"), InetAddress.getByName("127.0.0.1") });

        assertThatThrownBy(() -> validator.validateAndResolve(URI.create("https://example.com"))).isInstanceOf(UnknownHostException.class);
    }

    @Test
    void propagatesAddressResolutionFailure() {
        var validator = new LinkPreviewUrlValidator(host -> {
            throw new UnknownHostException(host);
        });

        assertThatThrownBy(() -> validator.validateAndResolve(URI.create("https://example.com"))).isInstanceOf(UnknownHostException.class);
    }

    private static Stream<Arguments> unsupportedAddresses() {
        return Stream.of("0.0.0.1", "10.0.0.1", "100.64.0.1", "127.0.0.1", "169.254.0.1", "172.16.0.1", "192.0.0.1", "192.0.2.1", "192.88.99.1", "192.168.0.1", "198.18.0.1",
                "198.51.100.1", "203.0.113.1", "224.0.0.1", "240.0.0.1", "::", "::1", "fe80::1", "fc00::1", "2001:db8::1", "2606:4700:4700::1111", "ff02::1", "::ffff:127.0.0.1")
                .map(Arguments::of);
    }
}
