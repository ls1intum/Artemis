package de.tum.cit.aet.artemis.communication.service.linkpreview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LinkPreviewAddressBoundaryTest {

    @ParameterizedTest
    @CsvSource({ "0.1.2.3, false", "100.63.255.255, true", "100.64.0.0, false", "100.127.255.255, false", "100.128.0.0, true", "192.0.0.1, false", "192.0.1.1, true",
            "192.0.2.1, false", "192.88.98.1, true", "192.88.99.1, false", "192.88.100.1, true", "198.17.255.255, true", "198.18.0.1, false", "198.19.255.255, false",
            "198.20.0.1, true", "198.51.99.1, true", "198.51.100.1, false", "198.51.101.1, true", "203.0.112.1, true", "203.0.113.1, false", "203.0.114.1, true",
            "224.0.0.1, false", "240.0.0.1, false" })
    void restrictsDnsResultsAtReservedAddressRangeBoundaries(String address, boolean allowed) throws Exception {
        var destination = ipv4(address);
        var validator = new LinkPreviewUrlValidator(host -> new InetAddress[] { destination });
        var uri = URI.create("https://example.org/preview");
        if (allowed) {
            var validated = validator.validateAndResolve(uri);
            assertThat(validated.uri()).isEqualTo(uri);
            assertThat(validated.addresses()).containsExactly(destination);
        }
        else {
            assertThatThrownBy(() -> validator.validateAndResolve(uri)).isInstanceOf(UnknownHostException.class).hasMessageContaining("public IPv4");
        }
    }

    @Test
    void rejectsMixedPrivateAndPublicDnsButIgnoresIpv6OnADualStackPublicHost() throws Exception {
        var publicAddress = ipv4("8.8.8.8");
        var privateAddress = ipv4("10.1.2.3");
        var ipv6 = InetAddress.getByAddress(new byte[16]);
        var uri = URI.create("https://example.org");
        var mixed = new LinkPreviewUrlValidator(host -> new InetAddress[] { publicAddress, privateAddress });
        assertThatThrownBy(() -> mixed.validateAndResolve(uri)).isInstanceOf(UnknownHostException.class);
        var dualStack = new LinkPreviewUrlValidator(host -> new InetAddress[] { ipv6, publicAddress });
        var result = dualStack.validateAndResolve(uri);
        assertThat(result.addresses()).containsExactly(publicAddress);
        assertThatThrownBy(() -> result.addresses().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    private static InetAddress ipv4(String value) throws UnknownHostException {
        var parts = Arrays.stream(value.split("\\.")).mapToInt(Integer::parseInt).toArray();
        return InetAddress.getByAddress(new byte[] { (byte) parts[0], (byte) parts[1], (byte) parts[2], (byte) parts[3] });
    }
}
