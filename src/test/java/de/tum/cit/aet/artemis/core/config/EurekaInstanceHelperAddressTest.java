package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.core.env.Environment;

/**
 * Unit tests for the address formatting utilities in {@link EurekaInstanceHelper}.
 * These tests verify correct handling of IPv4 and IPv6 addresses for Hazelcast cluster communication.
 */
class EurekaInstanceHelperAddressTest {

    private EurekaInstanceHelper eurekaInstanceHelper;

    @BeforeEach
    void setUp() {
        // Create mock dependencies - we only need the instance to test the utility methods
        DiscoveryClient discoveryClient = Mockito.mock(DiscoveryClient.class);
        Environment env = Mockito.mock(Environment.class);
        eurekaInstanceHelper = new EurekaInstanceHelper(discoveryClient, Optional.empty(), Optional.empty(), env);
    }

    @Nested
    class NormalizeHostTests {

        @ParameterizedTest(name = "IPv6 with brackets: {0} → {1}")
        @CsvSource({ "'[fcfe:0:0:0:0:0:a:1]', 'fcfe:0:0:0:0:0:a:1'", "'[::1]', '::1'", "'[2001:db8::1]', '2001:db8::1'", "'[fe80::1]', 'fe80::1'",
                "'[0:0:0:0:0:0:0:1]', '0:0:0:0:0:0:0:1'" })
        void shouldRemoveBracketsFromIPv6Addresses(String input, String expected) {
            assertThat(eurekaInstanceHelper.normalizeHost(input)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "IPv4 unchanged: {0}")
        @ValueSource(strings = { "192.168.1.1", "10.0.0.1", "172.16.0.1", "127.0.0.1", "0.0.0.0", "255.255.255.255" })
        void shouldLeaveIPv4AddressesUnchanged(String ipv4) {
            assertThat(eurekaInstanceHelper.normalizeHost(ipv4)).isEqualTo(ipv4);
        }

        @ParameterizedTest(name = "IPv6 without brackets unchanged: {0}")
        @ValueSource(strings = { "fcfe:0:0:0:0:0:a:1", "::1", "2001:db8::1", "fe80::1" })
        void shouldLeaveIPv6AddressesWithoutBracketsUnchanged(String ipv6) {
            assertThat(eurekaInstanceHelper.normalizeHost(ipv6)).isEqualTo(ipv6);
        }

        @Test
        void shouldReturnNullForNullInput() {
            assertThat(eurekaInstanceHelper.normalizeHost(null)).isNull();
        }

        @ParameterizedTest(name = "Empty or whitespace: ''{0}''")
        @ValueSource(strings = { "", " ", "  " })
        void shouldHandleEmptyAndWhitespaceStrings(String input) {
            assertThat(eurekaInstanceHelper.normalizeHost(input)).isEqualTo(input);
        }

        @ParameterizedTest(name = "Partial brackets unchanged: {0}")
        @ValueSource(strings = { "[192.168.1.1",     // Opening bracket only
                "192.168.1.1]",     // Closing bracket only
                "[::1",             // Opening bracket only for IPv6
                "::1]"              // Closing bracket only for IPv6
        })
        void shouldLeavePartialBracketsUnchanged(String input) {
            // Only properly matched brackets at start and end should be removed
            assertThat(eurekaInstanceHelper.normalizeHost(input)).isEqualTo(input);
        }

        @Test
        void shouldRemoveOnlyOuterBracketsFromDoubleBracketedInput() {
            // Double brackets: outer brackets are removed, inner brackets remain
            // This handles edge case of accidentally double-bracketed addresses
            assertThat(eurekaInstanceHelper.normalizeHost("[[::1]]")).isEqualTo("[::1]");
            assertThat(eurekaInstanceHelper.normalizeHost("[[2001:db8::1]]")).isEqualTo("[2001:db8::1]");
        }

        @Test
        void shouldHandleHostnames() {
            assertThat(eurekaInstanceHelper.normalizeHost("localhost")).isEqualTo("localhost");
            assertThat(eurekaInstanceHelper.normalizeHost("artemis.cit.tum.de")).isEqualTo("artemis.cit.tum.de");
        }
    }

    @Nested
    class FormatAddressForHazelcastTests {

        @ParameterizedTest(name = "IPv4: {0}:{1} → {2}")
        @CsvSource({ "'192.168.1.1', '5701', '192.168.1.1:5701'", "'10.0.0.1', '5701', '10.0.0.1:5701'", "'127.0.0.1', '8080', '127.0.0.1:8080'",
                "'0.0.0.0', '5701', '0.0.0.0:5701'", "'255.255.255.255', '5701', '255.255.255.255:5701'" })
        void shouldFormatIPv4AddressesWithoutBrackets(String host, String port, String expected) {
            assertThat(eurekaInstanceHelper.formatAddressForHazelcast(host, port)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "IPv6: {0}:{1} → {2}")
        @CsvSource({ "'fcfe:0:0:0:0:0:a:1', '5701', '[fcfe:0:0:0:0:0:a:1]:5701'", "'::1', '5701', '[::1]:5701'", "'2001:db8::1', '5701', '[2001:db8::1]:5701'",
                "'fe80::1', '5701', '[fe80::1]:5701'", "'0:0:0:0:0:0:0:1', '5701', '[0:0:0:0:0:0:0:1]:5701'", "'::ffff:192.168.1.1', '5701', '[::ffff:192.168.1.1]:5701'"  // IPv4-mapped
        // IPv6
        })
        void shouldFormatIPv6AddressesWithBrackets(String host, String port, String expected) {
            assertThat(eurekaInstanceHelper.formatAddressForHazelcast(host, port)).isEqualTo(expected);
        }

        @Test
        void shouldHandleDifferentPorts() {
            assertThat(eurekaInstanceHelper.formatAddressForHazelcast("192.168.1.1", "5701")).isEqualTo("192.168.1.1:5701");
            assertThat(eurekaInstanceHelper.formatAddressForHazelcast("192.168.1.1", "5702")).isEqualTo("192.168.1.1:5702");
            assertThat(eurekaInstanceHelper.formatAddressForHazelcast("192.168.1.1", "8080")).isEqualTo("192.168.1.1:8080");
            assertThat(eurekaInstanceHelper.formatAddressForHazelcast("::1", "5701")).isEqualTo("[::1]:5701");
            assertThat(eurekaInstanceHelper.formatAddressForHazelcast("::1", "443")).isEqualTo("[::1]:443");
        }

        @ParameterizedTest
        @NullSource
        void shouldThrowNullPointerExceptionForNullHost(String nullHost) {
            assertThatThrownBy(() -> eurekaInstanceHelper.formatAddressForHazelcast(nullHost, "5701")).isInstanceOf(NullPointerException.class);
        }

        @Test
        void shouldHandleHostnames() {
            // Hostnames don't contain colons, so they should be formatted like IPv4
            assertThat(eurekaInstanceHelper.formatAddressForHazelcast("localhost", "5701")).isEqualTo("localhost:5701");
            assertThat(eurekaInstanceHelper.formatAddressForHazelcast("artemis.cit.tum.de", "5701")).isEqualTo("artemis.cit.tum.de:5701");
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
            String normalizedHost = eurekaInstanceHelper.normalizeHost(eurekaHost);
            String hazelcastAddress = eurekaInstanceHelper.formatAddressForHazelcast(normalizedHost, port);
            assertThat(hazelcastAddress).isEqualTo(expectedHazelcast);
        }

        @Test
        void shouldHandleRealWorldEurekaScenarios() {
            // Scenario 1: IPv4 from Eureka (common case)
            String ipv4Host = "10.244.1.5";
            String normalized1 = eurekaInstanceHelper.normalizeHost(ipv4Host);
            assertThat(eurekaInstanceHelper.formatAddressForHazelcast(normalized1, "5701")).isEqualTo("10.244.1.5:5701");

            // Scenario 2: IPv6 from Eureka with brackets (staging environment)
            String ipv6Host = "[fcfe:0:0:0:0:0:a:1]";
            String normalized2 = eurekaInstanceHelper.normalizeHost(ipv6Host);
            assertThat(eurekaInstanceHelper.formatAddressForHazelcast(normalized2, "5701")).isEqualTo("[fcfe:0:0:0:0:0:a:1]:5701");

            // Scenario 3: IPv6 localhost with brackets
            String ipv6Localhost = "[::1]";
            String normalized3 = eurekaInstanceHelper.normalizeHost(ipv6Localhost);
            assertThat(eurekaInstanceHelper.formatAddressForHazelcast(normalized3, "5701")).isEqualTo("[::1]:5701");
        }
    }

    @Nested
    class EdgeCaseTests {

        @Test
        void shouldHandleEmptyStrings() {
            // Empty host - will result in ":port" which is technically invalid but we don't validate
            assertThat(eurekaInstanceHelper.formatAddressForHazelcast("", "5701")).isEqualTo(":5701");
            assertThat(eurekaInstanceHelper.normalizeHost("")).isEqualTo("");
        }

        @Test
        void shouldHandleEmptyPort() {
            assertThat(eurekaInstanceHelper.formatAddressForHazelcast("192.168.1.1", "")).isEqualTo("192.168.1.1:");
            assertThat(eurekaInstanceHelper.formatAddressForHazelcast("::1", "")).isEqualTo("[::1]:");
        }

        @Test
        void shouldHandleSpecialIPv6Addresses() {
            // Loopback
            assertThat(eurekaInstanceHelper.formatAddressForHazelcast("::1", "5701")).isEqualTo("[::1]:5701");

            // Unspecified address
            assertThat(eurekaInstanceHelper.formatAddressForHazelcast("::", "5701")).isEqualTo("[::]:5701");

            // Link-local (without zone ID)
            assertThat(eurekaInstanceHelper.formatAddressForHazelcast("fe80::1", "5701")).isEqualTo("[fe80::1]:5701");

            // IPv4-mapped IPv6 address
            assertThat(eurekaInstanceHelper.formatAddressForHazelcast("::ffff:192.168.1.1", "5701")).isEqualTo("[::ffff:192.168.1.1]:5701");
        }

        @Test
        void shouldPreserveAddressFormat() {
            // Full IPv6 format should be preserved
            String fullIpv6 = "2001:0db8:0000:0000:0000:0000:0000:0001";
            assertThat(eurekaInstanceHelper.formatAddressForHazelcast(fullIpv6, "5701")).isEqualTo("[" + fullIpv6 + "]:5701");

            // Compressed IPv6 format should be preserved
            String compressedIpv6 = "2001:db8::1";
            assertThat(eurekaInstanceHelper.formatAddressForHazelcast(compressedIpv6, "5701")).isEqualTo("[" + compressedIpv6 + "]:5701");
        }
    }

    @Nested
    class IsCurrentInstanceTests {

        private Registration registration;

        private ServiceInstance serviceInstance;

        private EurekaInstanceHelper helperWithRegistration;

        @BeforeEach
        void setUp() {
            registration = Mockito.mock(Registration.class);
            serviceInstance = Mockito.mock(ServiceInstance.class);
            DiscoveryClient discoveryClient = Mockito.mock(DiscoveryClient.class);
            Environment env = Mockito.mock(Environment.class);
            helperWithRegistration = new EurekaInstanceHelper(discoveryClient, Optional.of(registration), Optional.empty(), env);
        }

        @Test
        void shouldReturnFalseWhenNoRegistration() {
            // eurekaInstanceHelper has no registration (from outer setUp)
            ServiceInstance instance = Mockito.mock(ServiceInstance.class);
            assertThat(eurekaInstanceHelper.isCurrentInstance(instance)).isFalse();
        }

        @Test
        void shouldReturnFalseWhenPortsDoNotMatch() {
            when(registration.getPort()).thenReturn(8080);
            when(serviceInstance.getPort()).thenReturn(8081);

            assertThat(helperWithRegistration.isCurrentInstance(serviceInstance)).isFalse();
        }

        @Test
        void shouldReturnTrueWhenHostsMatch() {
            when(registration.getHost()).thenReturn("192.168.1.5");
            when(registration.getPort()).thenReturn(8080);
            when(serviceInstance.getHost()).thenReturn("192.168.1.5");
            when(serviceInstance.getPort()).thenReturn(8080);

            assertThat(helperWithRegistration.isCurrentInstance(serviceInstance)).isTrue();
        }

        @Test
        void shouldReturnTrueWhenLocalhostMatchesLoopbackIp() {
            // This tests the IP resolution fallback for Docker-style hostname/IP mismatches
            when(registration.getHost()).thenReturn("localhost");
            when(registration.getPort()).thenReturn(8080);
            when(serviceInstance.getHost()).thenReturn("127.0.0.1");
            when(serviceInstance.getPort()).thenReturn(8080);

            assertThat(helperWithRegistration.isCurrentInstance(serviceInstance)).isTrue();
        }

        @Test
        void shouldHandleIPv6LocalhostVariants() {
            // Test IPv6 loopback matches
            when(registration.getHost()).thenReturn("::1");
            when(registration.getPort()).thenReturn(8080);
            when(serviceInstance.getHost()).thenReturn("::1");
            when(serviceInstance.getPort()).thenReturn(8080);

            assertThat(helperWithRegistration.isCurrentInstance(serviceInstance)).isTrue();
        }

        @Test
        void shouldReturnFalseForDifferentIpAddresses() {
            when(registration.getHost()).thenReturn("192.168.1.5");
            when(registration.getPort()).thenReturn(8080);
            when(serviceInstance.getHost()).thenReturn("192.168.1.6");
            when(serviceInstance.getPort()).thenReturn(8080);

            assertThat(helperWithRegistration.isCurrentInstance(serviceInstance)).isFalse();
        }

        @Test
        void shouldHandleNormalizedBracketedIPv6() {
            // Eureka may store IPv6 with brackets
            when(registration.getHost()).thenReturn("[::1]");
            when(registration.getPort()).thenReturn(8080);
            when(serviceInstance.getHost()).thenReturn("[::1]");
            when(serviceInstance.getPort()).thenReturn(8080);

            assertThat(helperWithRegistration.isCurrentInstance(serviceInstance)).isTrue();
        }
    }

    @Nested
    class GetHazelcastHostTests {

        @Test
        void shouldPreferHazelcastHostFromMetadata() {
            ServiceInstance instance = Mockito.mock(ServiceInstance.class);
            when(instance.getMetadata()).thenReturn(Map.of("hazelcast.host", "192.168.1.100"));
            when(instance.getHost()).thenReturn("10.0.0.1");

            assertThat(eurekaInstanceHelper.getHazelcastHost(instance)).isEqualTo("192.168.1.100");
        }

        @Test
        void shouldFallbackToInstanceHostWhenMetadataEmpty() {
            ServiceInstance instance = Mockito.mock(ServiceInstance.class);
            when(instance.getMetadata()).thenReturn(Map.of());
            when(instance.getHost()).thenReturn("10.0.0.1");

            assertThat(eurekaInstanceHelper.getHazelcastHost(instance)).isEqualTo("10.0.0.1");
        }

        @Test
        void shouldNormalizeBracketedIPv6FromMetadata() {
            ServiceInstance instance = Mockito.mock(ServiceInstance.class);
            when(instance.getMetadata()).thenReturn(Map.of("hazelcast.host", "[::1]"));
            when(instance.getHost()).thenReturn("10.0.0.1");

            assertThat(eurekaInstanceHelper.getHazelcastHost(instance)).isEqualTo("::1");
        }
    }
}
