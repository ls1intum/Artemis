package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.env.Environment;

/**
 * Unit tests for the address formatting utilities in {@link HazelcastConnection}.
 * These tests verify correct handling of IPv4 and IPv6 addresses for Hazelcast cluster communication.
 */
class HazelcastConnectionAddressTest {

    private HazelcastConnection hazelcastConnection;

    @BeforeEach
    void setUp() {
        // Create mock dependencies - we only need the instance to test the utility methods
        DiscoveryClient discoveryClient = Mockito.mock(DiscoveryClient.class);
        Environment env = Mockito.mock(Environment.class);
        hazelcastConnection = new HazelcastConnection(discoveryClient, Optional.empty(), Optional.empty(), env);
    }

    @Nested
    class NormalizeHostTests {

        @ParameterizedTest(name = "IPv6 with brackets: {0} → {1}")
        @CsvSource({ "'[fcfe:0:0:0:0:0:a:1]', 'fcfe:0:0:0:0:0:a:1'", "'[::1]', '::1'", "'[2001:db8::1]', '2001:db8::1'", "'[fe80::1]', 'fe80::1'",
                "'[0:0:0:0:0:0:0:1]', '0:0:0:0:0:0:0:1'" })
        void shouldRemoveBracketsFromIPv6Addresses(String input, String expected) {
            assertThat(hazelcastConnection.normalizeHost(input)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "IPv4 unchanged: {0}")
        @ValueSource(strings = { "192.168.1.1", "10.0.0.1", "172.16.0.1", "127.0.0.1", "0.0.0.0", "255.255.255.255" })
        void shouldLeaveIPv4AddressesUnchanged(String ipv4) {
            assertThat(hazelcastConnection.normalizeHost(ipv4)).isEqualTo(ipv4);
        }

        @ParameterizedTest(name = "IPv6 without brackets unchanged: {0}")
        @ValueSource(strings = { "fcfe:0:0:0:0:0:a:1", "::1", "2001:db8::1", "fe80::1" })
        void shouldLeaveIPv6AddressesWithoutBracketsUnchanged(String ipv6) {
            assertThat(hazelcastConnection.normalizeHost(ipv6)).isEqualTo(ipv6);
        }

        @Test
        void shouldReturnNullForNullInput() {
            assertThat(hazelcastConnection.normalizeHost(null)).isNull();
        }

        @ParameterizedTest(name = "Empty or whitespace: ''{0}''")
        @ValueSource(strings = { "", " ", "  " })
        void shouldHandleEmptyAndWhitespaceStrings(String input) {
            assertThat(hazelcastConnection.normalizeHost(input)).isEqualTo(input);
        }

        @ParameterizedTest(name = "Partial brackets unchanged: {0}")
        @ValueSource(strings = { "[192.168.1.1",     // Opening bracket only
                "192.168.1.1]",     // Closing bracket only
                "[::1",             // Opening bracket only for IPv6
                "::1]"              // Closing bracket only for IPv6
        })
        void shouldLeavePartialBracketsUnchanged(String input) {
            // Only properly matched brackets at start and end should be removed
            assertThat(hazelcastConnection.normalizeHost(input)).isEqualTo(input);
        }

        @Test
        void shouldRemoveOnlyOuterBracketsFromDoubleBracketedInput() {
            // Double brackets: outer brackets are removed, inner brackets remain
            // This handles edge case of accidentally double-bracketed addresses
            assertThat(hazelcastConnection.normalizeHost("[[::1]]")).isEqualTo("[::1]");
            assertThat(hazelcastConnection.normalizeHost("[[2001:db8::1]]")).isEqualTo("[2001:db8::1]");
        }

        @Test
        void shouldHandleHostnames() {
            assertThat(hazelcastConnection.normalizeHost("localhost")).isEqualTo("localhost");
            assertThat(hazelcastConnection.normalizeHost("artemis.cit.tum.de")).isEqualTo("artemis.cit.tum.de");
        }
    }

    @Nested
    class FormatAddressForHazelcastTests {

        @ParameterizedTest(name = "IPv4: {0}:{1} → {2}")
        @CsvSource({ "'192.168.1.1', '5701', '192.168.1.1:5701'", "'10.0.0.1', '5701', '10.0.0.1:5701'", "'127.0.0.1', '8080', '127.0.0.1:8080'",
                "'0.0.0.0', '5701', '0.0.0.0:5701'", "'255.255.255.255', '5701', '255.255.255.255:5701'" })
        void shouldFormatIPv4AddressesWithoutBrackets(String host, String port, String expected) {
            assertThat(hazelcastConnection.formatAddressForHazelcast(host, port)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "IPv6: {0}:{1} → {2}")
        @CsvSource({ "'fcfe:0:0:0:0:0:a:1', '5701', '[fcfe:0:0:0:0:0:a:1]:5701'", "'::1', '5701', '[::1]:5701'", "'2001:db8::1', '5701', '[2001:db8::1]:5701'",
                "'fe80::1', '5701', '[fe80::1]:5701'", "'0:0:0:0:0:0:0:1', '5701', '[0:0:0:0:0:0:0:1]:5701'", "'::ffff:192.168.1.1', '5701', '[::ffff:192.168.1.1]:5701'"  // IPv4-mapped
                                                                                                                                                                           // IPv6
        })
        void shouldFormatIPv6AddressesWithBrackets(String host, String port, String expected) {
            assertThat(hazelcastConnection.formatAddressForHazelcast(host, port)).isEqualTo(expected);
        }

        @Test
        void shouldHandleDifferentPorts() {
            assertThat(hazelcastConnection.formatAddressForHazelcast("192.168.1.1", "5701")).isEqualTo("192.168.1.1:5701");
            assertThat(hazelcastConnection.formatAddressForHazelcast("192.168.1.1", "5702")).isEqualTo("192.168.1.1:5702");
            assertThat(hazelcastConnection.formatAddressForHazelcast("192.168.1.1", "8080")).isEqualTo("192.168.1.1:8080");
            assertThat(hazelcastConnection.formatAddressForHazelcast("::1", "5701")).isEqualTo("[::1]:5701");
            assertThat(hazelcastConnection.formatAddressForHazelcast("::1", "443")).isEqualTo("[::1]:443");
        }

        @ParameterizedTest
        @NullSource
        void shouldThrowNullPointerExceptionForNullHost(String nullHost) {
            assertThatThrownBy(() -> hazelcastConnection.formatAddressForHazelcast(nullHost, "5701")).isInstanceOf(NullPointerException.class);
        }

        @Test
        void shouldHandleHostnames() {
            // Hostnames don't contain colons, so they should be formatted like IPv4
            assertThat(hazelcastConnection.formatAddressForHazelcast("localhost", "5701")).isEqualTo("localhost:5701");
            assertThat(hazelcastConnection.formatAddressForHazelcast("artemis.cit.tum.de", "5701")).isEqualTo("artemis.cit.tum.de:5701");
        }
    }

    @Nested
    class IntegrationOfNormalizeAndFormatTests {

        @ParameterizedTest(name = "Eureka format to Hazelcast: {0} → {2}")
        @CsvSource({
                // Eureka provides IPv6 with brackets, we normalize then format
                "'[fcfe:0:0:0:0:0:a:1]', '5701', '[fcfe:0:0:0:0:0:a:1]:5701'", "'[::1]', '5701', '[::1]:5701'", "'[2001:db8::1]', '5701', '[2001:db8::1]:5701'",
                // IPv4 from Eureka (no brackets)
                "'192.168.1.1', '5701', '192.168.1.1:5701'", "'10.0.0.1', '5701', '10.0.0.1:5701'" })
        void shouldCorrectlyProcessEurekaHostsToHazelcastFormat(String eurekaHost, String port, String expectedHazelcast) {
            // This simulates the actual usage pattern: normalize first, then format
            String normalizedHost = hazelcastConnection.normalizeHost(eurekaHost);
            String hazelcastAddress = hazelcastConnection.formatAddressForHazelcast(normalizedHost, port);
            assertThat(hazelcastAddress).isEqualTo(expectedHazelcast);
        }

        @Test
        void shouldHandleRealWorldEurekaScenarios() {
            // Scenario 1: IPv4 from Eureka (common case)
            String ipv4Host = "10.244.1.5";
            String normalized1 = hazelcastConnection.normalizeHost(ipv4Host);
            assertThat(hazelcastConnection.formatAddressForHazelcast(normalized1, "5701")).isEqualTo("10.244.1.5:5701");

            // Scenario 2: IPv6 from Eureka with brackets (staging environment)
            String ipv6Host = "[fcfe:0:0:0:0:0:a:1]";
            String normalized2 = hazelcastConnection.normalizeHost(ipv6Host);
            assertThat(hazelcastConnection.formatAddressForHazelcast(normalized2, "5701")).isEqualTo("[fcfe:0:0:0:0:0:a:1]:5701");

            // Scenario 3: IPv6 localhost with brackets
            String ipv6Localhost = "[::1]";
            String normalized3 = hazelcastConnection.normalizeHost(ipv6Localhost);
            assertThat(hazelcastConnection.formatAddressForHazelcast(normalized3, "5701")).isEqualTo("[::1]:5701");
        }
    }

    @Nested
    class EdgeCaseTests {

        @Test
        void shouldHandleEmptyStrings() {
            // Empty host - will result in ":port" which is technically invalid but we don't validate
            assertThat(hazelcastConnection.formatAddressForHazelcast("", "5701")).isEqualTo(":5701");
            assertThat(hazelcastConnection.normalizeHost("")).isEqualTo("");
        }

        @Test
        void shouldHandleEmptyPort() {
            assertThat(hazelcastConnection.formatAddressForHazelcast("192.168.1.1", "")).isEqualTo("192.168.1.1:");
            assertThat(hazelcastConnection.formatAddressForHazelcast("::1", "")).isEqualTo("[::1]:");
        }

        @Test
        void shouldHandleSpecialIPv6Addresses() {
            // Loopback
            assertThat(hazelcastConnection.formatAddressForHazelcast("::1", "5701")).isEqualTo("[::1]:5701");

            // Unspecified address
            assertThat(hazelcastConnection.formatAddressForHazelcast("::", "5701")).isEqualTo("[::]:5701");

            // Link-local (without zone ID)
            assertThat(hazelcastConnection.formatAddressForHazelcast("fe80::1", "5701")).isEqualTo("[fe80::1]:5701");

            // IPv4-mapped IPv6 address
            assertThat(hazelcastConnection.formatAddressForHazelcast("::ffff:192.168.1.1", "5701")).isEqualTo("[::ffff:192.168.1.1]:5701");
        }

        @Test
        void shouldPreserveAddressFormat() {
            // Full IPv6 format should be preserved
            String fullIpv6 = "2001:0db8:0000:0000:0000:0000:0000:0001";
            assertThat(hazelcastConnection.formatAddressForHazelcast(fullIpv6, "5701")).isEqualTo("[" + fullIpv6 + "]:5701");

            // Compressed IPv6 format should be preserved
            String compressedIpv6 = "2001:db8::1";
            assertThat(hazelcastConnection.formatAddressForHazelcast(compressedIpv6, "5701")).isEqualTo("[" + compressedIpv6 + "]:5701");
        }
    }
}
