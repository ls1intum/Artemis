package de.tum.cit.aet.artemis.core.config;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.cluster.Address;
import com.hazelcast.logging.ILogger;
import com.hazelcast.spi.discovery.AbstractDiscoveryStrategy;
import com.hazelcast.spi.discovery.DiscoveryNode;
import com.hazelcast.spi.discovery.SimpleDiscoveryNode;

/**
 * A Hazelcast DiscoveryStrategy that discovers core cluster nodes from the Eureka service registry.
 * This allows Hazelcast clients (build agents) to dynamically discover and connect to core nodes
 * without static address configuration.
 *
 * <p>
 * The strategy uses {@link EurekaInstanceHelper#discoverCoreNodeAddresses()} to query the service
 * registry for available core nodes. Discovery is performed each time Hazelcast calls
 * {@link #discoverNodes()}, which happens during initial connection and reconnection attempts.
 *
 * <p>
 * The {@link EurekaInstanceHelper} is passed through constructor injection from the
 * {@link EurekaHazelcastDiscoveryStrategyFactory}, eliminating the need for static holders.
 */
public class EurekaHazelcastDiscoveryStrategy extends AbstractDiscoveryStrategy {

    private static final Logger log = LoggerFactory.getLogger(EurekaHazelcastDiscoveryStrategy.class);

    /**
     * Pattern to parse Hazelcast address format: "host:port" or "[ipv6]:port"
     * Group 1: IPv6 address (without brackets)
     * Group 2: IPv4/hostname
     * Group 3: Port number
     */
    private static final Pattern ADDRESS_PATTERN = Pattern.compile("^(?:\\[([^]]+)]|([^:]+)):(\\d+)$");

    private final EurekaInstanceHelper eurekaInstanceHelper;

    /**
     * Creates a new discovery strategy with the given Hazelcast logger, properties, and EurekaInstanceHelper.
     *
     * @param hazelcastLogger      the Hazelcast logger
     * @param properties           configuration properties
     * @param eurekaInstanceHelper the helper for discovering service instances from Eureka
     */
    public EurekaHazelcastDiscoveryStrategy(ILogger hazelcastLogger, Map<String, Comparable> properties, EurekaInstanceHelper eurekaInstanceHelper) {
        super(hazelcastLogger, properties);
        this.eurekaInstanceHelper = eurekaInstanceHelper;
    }

    /**
     * Discovers core cluster nodes from the Eureka service registry.
     * This method is called by Hazelcast during initial connection and reconnection attempts.
     *
     * @return an iterable of discovered nodes, or empty if no nodes found or discovery fails
     */
    @Override
    public Iterable<DiscoveryNode> discoverNodes() {
        log.info("Hazelcast discovery strategy discoverNodes() called");
        if (eurekaInstanceHelper == null) {
            log.warn("EurekaInstanceHelper not available - cannot discover core nodes. Returning empty list.");
            return Collections.emptyList();
        }

        log.info("EurekaInstanceHelper is available, querying for core node addresses");
        List<String> addresses = eurekaInstanceHelper.discoverCoreNodeAddresses();
        if (addresses.isEmpty()) {
            log.warn("No core nodes discovered from service registry. Returning empty list.");
            return Collections.emptyList();
        }

        log.info("Discovered {} core node(s) from service registry: {}", addresses.size(), addresses);
        List<DiscoveryNode> nodes = addresses.stream().map(this::parseAddress).filter(Objects::nonNull).toList();
        log.info("Returning {} valid discovery nodes to Hazelcast", nodes.size());
        return nodes;
    }

    /**
     * Parses an address string in Hazelcast format to a DiscoveryNode.
     *
     * @param addressStr the address string in format "host:port" or "[ipv6]:port"
     * @return a DiscoveryNode, or null if parsing fails
     */
    private DiscoveryNode parseAddress(String addressStr) {
        try {
            Matcher matcher = ADDRESS_PATTERN.matcher(addressStr);
            if (!matcher.matches()) {
                log.warn("Cannot parse address '{}' - invalid format", addressStr);
                return null;
            }

            // Group 1 is IPv6 (without brackets), Group 2 is IPv4/hostname
            String host = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            int port = Integer.parseInt(matcher.group(3));

            InetAddress inetAddress = InetAddress.getByName(host);
            Address hazelcastAddress = new Address(inetAddress, port);

            log.debug("Parsed discovery address: {}", hazelcastAddress);
            return new SimpleDiscoveryNode(hazelcastAddress);
        }
        catch (UnknownHostException e) {
            log.warn("Cannot resolve host in address '{}': {}", addressStr, e.getMessage());
            return null;
        }
        catch (NumberFormatException e) {
            log.warn("Cannot parse port in address '{}': {}", addressStr, e.getMessage());
            return null;
        }
    }

    @Override
    public void start() {
        log.info("Eureka Hazelcast discovery strategy started");
    }

    @Override
    public void destroy() {
        log.info("Eureka Hazelcast discovery strategy stopped");
    }
}
