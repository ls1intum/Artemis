package de.tum.cit.aet.artemis.core.config;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import com.hazelcast.config.properties.PropertyDefinition;
import com.hazelcast.logging.ILogger;
import com.hazelcast.spi.discovery.DiscoveryNode;
import com.hazelcast.spi.discovery.DiscoveryStrategy;
import com.hazelcast.spi.discovery.DiscoveryStrategyFactory;

/**
 * Factory for creating {@link EurekaHazelcastDiscoveryStrategy} instances.
 * This factory is registered with Hazelcast's discovery SPI to enable automatic
 * discovery of core cluster nodes from the Eureka service registry.
 *
 * <p>
 * The factory creates discovery strategy instances that query the service registry
 * for available core nodes, enabling Hazelcast clients (build agents) to dynamically
 * discover and connect to the cluster without static address configuration.
 */
public class EurekaHazelcastDiscoveryStrategyFactory implements DiscoveryStrategyFactory {

    /**
     * Returns the type of DiscoveryStrategy this factory creates.
     *
     * @return the EurekaHazelcastDiscoveryStrategy class
     */
    @Override
    public Class<? extends DiscoveryStrategy> getDiscoveryStrategyType() {
        return EurekaHazelcastDiscoveryStrategy.class;
    }

    /**
     * Creates a new instance of the Eureka discovery strategy.
     *
     * @param discoveryNode the local discovery node (may be null for clients)
     * @param logger        the Hazelcast logger
     * @param properties    configuration properties (not currently used)
     * @return a new EurekaHazelcastDiscoveryStrategy instance
     */
    @Override
    @SuppressWarnings({ "rawtypes" })
    public DiscoveryStrategy newDiscoveryStrategy(DiscoveryNode discoveryNode, ILogger logger, Map<String, Comparable> properties) {
        return new EurekaHazelcastDiscoveryStrategy(logger, properties);
    }

    /**
     * Returns the configuration properties supported by this discovery strategy.
     * Currently, no additional properties are required as the strategy uses
     * the HazelcastConnection bean set via the static holder.
     *
     * @return an empty collection (no additional properties required)
     */
    @Override
    public Collection<PropertyDefinition> getConfigurationProperties() {
        return Collections.emptyList();
    }
}
