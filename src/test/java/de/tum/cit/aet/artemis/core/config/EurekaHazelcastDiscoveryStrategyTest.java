package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.hazelcast.logging.ILogger;
import com.hazelcast.spi.discovery.DiscoveryNode;

/**
 * Unit tests for {@link EurekaHazelcastDiscoveryStrategy}.
 * These tests verify the discovery strategy correctly discovers and parses node addresses
 * from the Eureka service registry via {@link EurekaInstanceHelper}.
 */
class EurekaHazelcastDiscoveryStrategyTest {

    private EurekaHazelcastDiscoveryStrategy discoveryStrategy;

    private EurekaInstanceHelper eurekaInstanceHelper;

    @BeforeEach
    void setUp() {
        // Create mock EurekaInstanceHelper
        eurekaInstanceHelper = mock(EurekaInstanceHelper.class);

        // Create the discovery strategy with a mock logger and the helper via constructor
        ILogger mockLogger = mock(ILogger.class);
        discoveryStrategy = new EurekaHazelcastDiscoveryStrategy(mockLogger, Collections.emptyMap(), eurekaInstanceHelper);
    }

    @Nested
    class DiscoverNodesTests {

        @Test
        void shouldReturnEmptyWhenEurekaInstanceHelperIsNull() {
            // Create a strategy with null helper
            ILogger mockLogger = mock(ILogger.class);
            EurekaHazelcastDiscoveryStrategy strategyWithNullHelper = new EurekaHazelcastDiscoveryStrategy(mockLogger, Collections.emptyMap(), null);

            Iterable<DiscoveryNode> nodes = strategyWithNullHelper.discoverNodes();

            assertThat(nodes).isEmpty();
        }

        @Test
        void shouldReturnEmptyWhenNoNodesDiscovered() {
            when(eurekaInstanceHelper.discoverCoreNodeAddresses()).thenReturn(Collections.emptyList());

            Iterable<DiscoveryNode> nodes = discoveryStrategy.discoverNodes();

            assertThat(nodes).isEmpty();
        }

        @Test
        void shouldDiscoverSingleIPv4Node() {
            when(eurekaInstanceHelper.discoverCoreNodeAddresses()).thenReturn(List.of("192.168.1.1:5701"));

            Iterable<DiscoveryNode> nodes = discoveryStrategy.discoverNodes();

            List<DiscoveryNode> nodeList = StreamSupport.stream(nodes.spliterator(), false).toList();
            assertThat(nodeList).hasSize(1);
            assertThat(nodeList.getFirst().getPublicAddress().getHost()).isEqualTo("192.168.1.1");
            assertThat(nodeList.getFirst().getPublicAddress().getPort()).isEqualTo(5701);
        }

        @Test
        void shouldDiscoverSingleIPv6Node() {
            when(eurekaInstanceHelper.discoverCoreNodeAddresses()).thenReturn(List.of("[::1]:5701"));

            Iterable<DiscoveryNode> nodes = discoveryStrategy.discoverNodes();

            List<DiscoveryNode> nodeList = StreamSupport.stream(nodes.spliterator(), false).toList();
            assertThat(nodeList).hasSize(1);
            // IPv6 loopback resolves to 0:0:0:0:0:0:0:1 in expanded form
            assertThat(nodeList.getFirst().getPublicAddress().getPort()).isEqualTo(5701);
        }

        @Test
        void shouldDiscoverMultipleNodes() {
            when(eurekaInstanceHelper.discoverCoreNodeAddresses()).thenReturn(List.of("192.168.1.1:5701", "192.168.1.2:5701", "192.168.1.3:5702"));

            Iterable<DiscoveryNode> nodes = discoveryStrategy.discoverNodes();

            List<DiscoveryNode> nodeList = StreamSupport.stream(nodes.spliterator(), false).toList();
            assertThat(nodeList).hasSize(3);
        }

        @Test
        void shouldHandleMixedIPv4AndIPv6Nodes() {
            when(eurekaInstanceHelper.discoverCoreNodeAddresses()).thenReturn(List.of("192.168.1.1:5701", "[2001:db8::1]:5701"));

            Iterable<DiscoveryNode> nodes = discoveryStrategy.discoverNodes();

            List<DiscoveryNode> nodeList = StreamSupport.stream(nodes.spliterator(), false).toList();
            assertThat(nodeList).hasSize(2);
        }

        @Test
        void shouldFilterOutInvalidAddresses() {
            // Mix valid and invalid addresses
            when(eurekaInstanceHelper.discoverCoreNodeAddresses()).thenReturn(List.of("192.168.1.1:5701", "invalid-address", "192.168.1.2:5701"));

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
            when(eurekaInstanceHelper.discoverCoreNodeAddresses()).thenReturn(List.of(address));

            Iterable<DiscoveryNode> nodes = discoveryStrategy.discoverNodes();

            List<DiscoveryNode> nodeList = StreamSupport.stream(nodes.spliterator(), false).toList();
            assertThat(nodeList).hasSize(1);
            assertThat(nodeList.getFirst().getPublicAddress().getHost()).isEqualTo(expectedHost);
            assertThat(nodeList.getFirst().getPublicAddress().getPort()).isEqualTo(expectedPort);
        }

        @ParameterizedTest(name = "IPv6 address: {0}")
        @ValueSource(strings = { "[::1]:5701", "[2001:db8::1]:5701", "[fe80::1]:5701", "[0:0:0:0:0:0:0:1]:5701" })
        void shouldParseIPv6Addresses(String address) {
            when(eurekaInstanceHelper.discoverCoreNodeAddresses()).thenReturn(List.of(address));

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
            when(eurekaInstanceHelper.discoverCoreNodeAddresses()).thenReturn(List.of(invalidAddress));

            Iterable<DiscoveryNode> nodes = discoveryStrategy.discoverNodes();

            assertThat(nodes).isEmpty();
        }

        @Test
        void shouldHandleHostnameResolutionFailure() {
            // Use an invalid hostname that cannot be resolved
            when(eurekaInstanceHelper.discoverCoreNodeAddresses()).thenReturn(List.of("invalid.hostname.that.does.not.exist.local:5701"));

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
    class FactoryTests {

        @Test
        void factoryShouldCreateStrategyWithHelper() {
            EurekaHazelcastDiscoveryStrategyFactory factory = new EurekaHazelcastDiscoveryStrategyFactory(eurekaInstanceHelper);
            ILogger mockLogger = mock(ILogger.class);

            var strategy = factory.newDiscoveryStrategy(null, mockLogger, Collections.emptyMap());

            assertThat(strategy).isInstanceOf(EurekaHazelcastDiscoveryStrategy.class);

            // Verify the strategy can discover nodes (meaning the helper was passed correctly)
            when(eurekaInstanceHelper.discoverCoreNodeAddresses()).thenReturn(List.of("192.168.1.1:5701"));
            Iterable<DiscoveryNode> nodes = strategy.discoverNodes();
            assertThat(nodes).isNotEmpty();
        }

        @Test
        void factoryShouldReturnCorrectStrategyType() {
            EurekaHazelcastDiscoveryStrategyFactory factory = new EurekaHazelcastDiscoveryStrategyFactory(eurekaInstanceHelper);

            assertThat(factory.getDiscoveryStrategyType()).isEqualTo(EurekaHazelcastDiscoveryStrategy.class);
        }

        @Test
        void factoryShouldReturnEmptyConfigurationProperties() {
            EurekaHazelcastDiscoveryStrategyFactory factory = new EurekaHazelcastDiscoveryStrategyFactory(eurekaInstanceHelper);

            assertThat(factory.getConfigurationProperties()).isEmpty();
        }
    }
}
