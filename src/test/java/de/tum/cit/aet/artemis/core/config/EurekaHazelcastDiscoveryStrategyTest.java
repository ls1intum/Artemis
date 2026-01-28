package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.env.Environment;

import com.hazelcast.spi.discovery.DiscoveryNode;

/**
 * Unit tests for {@link EurekaHazelcastDiscoveryStrategy}.
 * These tests verify the discovery strategy correctly discovers and parses node addresses
 * from the Eureka service registry via {@link HazelcastConnection}.
 */
class EurekaHazelcastDiscoveryStrategyTest {

    private EurekaHazelcastDiscoveryStrategy discoveryStrategy;

    private HazelcastConnection hazelcastConnection;

    @BeforeEach
    void setUp() {
        // Create mock HazelcastConnection
        DiscoveryClient discoveryClient = mock(DiscoveryClient.class);
        Environment env = mock(Environment.class);
        hazelcastConnection = mock(HazelcastConnection.class);

        // Set the mock in the static holder
        EurekaHazelcastDiscoveryStrategy.setHazelcastConnection(hazelcastConnection);

        // Create the discovery strategy
        discoveryStrategy = new EurekaHazelcastDiscoveryStrategy(Collections.emptyMap());
    }

    @AfterEach
    void tearDown() {
        // Clear the static holder to prevent test pollution
        EurekaHazelcastDiscoveryStrategy.clearHazelcastConnection();
    }

    @Nested
    class DiscoverNodesTests {

        @Test
        void shouldReturnEmptyWhenHazelcastConnectionNotSet() {
            // Clear the connection
            EurekaHazelcastDiscoveryStrategy.clearHazelcastConnection();

            Iterable<DiscoveryNode> nodes = discoveryStrategy.discoverNodes();

            assertThat(nodes).isEmpty();
        }

        @Test
        void shouldReturnEmptyWhenNoNodesDiscovered() {
            when(hazelcastConnection.discoverCoreNodeAddresses()).thenReturn(Collections.emptyList());

            Iterable<DiscoveryNode> nodes = discoveryStrategy.discoverNodes();

            assertThat(nodes).isEmpty();
        }

        @Test
        void shouldDiscoverSingleIPv4Node() {
            when(hazelcastConnection.discoverCoreNodeAddresses()).thenReturn(List.of("192.168.1.1:5701"));

            Iterable<DiscoveryNode> nodes = discoveryStrategy.discoverNodes();

            List<DiscoveryNode> nodeList = StreamSupport.stream(nodes.spliterator(), false).toList();
            assertThat(nodeList).hasSize(1);
            assertThat(nodeList.getFirst().getPublicAddress().getHost()).isEqualTo("192.168.1.1");
            assertThat(nodeList.getFirst().getPublicAddress().getPort()).isEqualTo(5701);
        }

        @Test
        void shouldDiscoverSingleIPv6Node() {
            when(hazelcastConnection.discoverCoreNodeAddresses()).thenReturn(List.of("[::1]:5701"));

            Iterable<DiscoveryNode> nodes = discoveryStrategy.discoverNodes();

            List<DiscoveryNode> nodeList = StreamSupport.stream(nodes.spliterator(), false).toList();
            assertThat(nodeList).hasSize(1);
            // IPv6 loopback resolves to 0:0:0:0:0:0:0:1 in expanded form
            assertThat(nodeList.getFirst().getPublicAddress().getPort()).isEqualTo(5701);
        }

        @Test
        void shouldDiscoverMultipleNodes() {
            when(hazelcastConnection.discoverCoreNodeAddresses()).thenReturn(List.of("192.168.1.1:5701", "192.168.1.2:5701", "192.168.1.3:5702"));

            Iterable<DiscoveryNode> nodes = discoveryStrategy.discoverNodes();

            List<DiscoveryNode> nodeList = StreamSupport.stream(nodes.spliterator(), false).toList();
            assertThat(nodeList).hasSize(3);
        }

        @Test
        void shouldHandleMixedIPv4AndIPv6Nodes() {
            when(hazelcastConnection.discoverCoreNodeAddresses()).thenReturn(List.of("192.168.1.1:5701", "[2001:db8::1]:5701"));

            Iterable<DiscoveryNode> nodes = discoveryStrategy.discoverNodes();

            List<DiscoveryNode> nodeList = StreamSupport.stream(nodes.spliterator(), false).toList();
            assertThat(nodeList).hasSize(2);
        }

        @Test
        void shouldFilterOutInvalidAddresses() {
            // Mix valid and invalid addresses
            when(hazelcastConnection.discoverCoreNodeAddresses()).thenReturn(List.of("192.168.1.1:5701", "invalid-address", "192.168.1.2:5701"));

            Iterable<DiscoveryNode> nodes = discoveryStrategy.discoverNodes();

            // Should only return the 2 valid addresses
            List<DiscoveryNode> nodeList = StreamSupport.stream(nodes.spliterator(), false).toList();
            assertThat(nodeList).hasSize(2);
        }
    }

    @Nested
    class AddressParsingTests {

        @ParameterizedTest(name = "IPv4 address: {0}")
        @CsvSource({ "'192.168.1.1:5701', '192.168.1.1', 5701", "'10.0.0.1:5702', '10.0.0.1', 5702", "'127.0.0.1:8080', '127.0.0.1', 8080",
                "'255.255.255.255:5701', '255.255.255.255', 5701" })
        void shouldParseIPv4Addresses(String address, String expectedHost, int expectedPort) {
            when(hazelcastConnection.discoverCoreNodeAddresses()).thenReturn(List.of(address));

            Iterable<DiscoveryNode> nodes = discoveryStrategy.discoverNodes();

            List<DiscoveryNode> nodeList = StreamSupport.stream(nodes.spliterator(), false).toList();
            assertThat(nodeList).hasSize(1);
            assertThat(nodeList.getFirst().getPublicAddress().getHost()).isEqualTo(expectedHost);
            assertThat(nodeList.getFirst().getPublicAddress().getPort()).isEqualTo(expectedPort);
        }

        @ParameterizedTest(name = "IPv6 address: {0}")
        @ValueSource(strings = { "[::1]:5701", "[2001:db8::1]:5701", "[fe80::1]:5701", "[0:0:0:0:0:0:0:1]:5701" })
        void shouldParseIPv6Addresses(String address) {
            when(hazelcastConnection.discoverCoreNodeAddresses()).thenReturn(List.of(address));

            Iterable<DiscoveryNode> nodes = discoveryStrategy.discoverNodes();

            List<DiscoveryNode> nodeList = StreamSupport.stream(nodes.spliterator(), false).toList();
            assertThat(nodeList).hasSize(1);
            assertThat(nodeList.getFirst().getPublicAddress().getPort()).isEqualTo(5701);
        }

        @ParameterizedTest(name = "Invalid address: {0}")
        @ValueSource(strings = { "invalid", "192.168.1.1", // missing port
                ":5701", // missing host
                "192.168.1.1:", // empty port
                "192.168.1.1:abc", // non-numeric port
                "[::1]", // IPv6 missing port
                "::1:5701" // IPv6 without brackets
        })
        void shouldSkipInvalidAddresses(String invalidAddress) {
            when(hazelcastConnection.discoverCoreNodeAddresses()).thenReturn(List.of(invalidAddress));

            Iterable<DiscoveryNode> nodes = discoveryStrategy.discoverNodes();

            assertThat(nodes).isEmpty();
        }

        @Test
        void shouldHandleHostnameResolutionFailure() {
            // Use an invalid hostname that cannot be resolved
            when(hazelcastConnection.discoverCoreNodeAddresses()).thenReturn(List.of("invalid.hostname.that.does.not.exist.local:5701"));

            Iterable<DiscoveryNode> nodes = discoveryStrategy.discoverNodes();

            // Should return empty because hostname cannot be resolved
            assertThat(nodes).isEmpty();
        }
    }

    @Nested
    class LifecycleTests {

        @Test
        void shouldStartWithoutError() {
            // Should not throw
            discoveryStrategy.start();
        }

        @Test
        void shouldDestroyWithoutError() {
            // Should not throw
            discoveryStrategy.destroy();
        }
    }

    @Nested
    class StaticHolderTests {

        @Test
        void shouldSetAndClearHazelcastConnection() {
            // First verify it's set
            when(hazelcastConnection.discoverCoreNodeAddresses()).thenReturn(List.of("192.168.1.1:5701"));
            assertThat(discoveryStrategy.discoverNodes()).isNotEmpty();

            // Clear it
            EurekaHazelcastDiscoveryStrategy.clearHazelcastConnection();

            // Now discovery should return empty
            assertThat(discoveryStrategy.discoverNodes()).isEmpty();

            // Set it again
            EurekaHazelcastDiscoveryStrategy.setHazelcastConnection(hazelcastConnection);

            // Discovery should work again
            assertThat(discoveryStrategy.discoverNodes()).isNotEmpty();
        }
    }
}
