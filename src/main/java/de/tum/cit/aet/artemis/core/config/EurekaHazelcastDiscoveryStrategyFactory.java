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
 *
 * <p>
 * The {@link EurekaInstanceHelper} is passed through constructor injection, eliminating
 * the need for static holders and enabling proper dependency management.
 */
public class EurekaHazelcastDiscoveryStrategyFactory implements DiscoveryStrategyFactory {

    private final EurekaInstanceHelper eurekaInstanceHelper;

    /**
     * Creates a new factory with the given EurekaInstanceHelper.
     *
     * @param eurekaInstanceHelper the helper for discovering service instances from Eureka
     */
    public EurekaHazelcastDiscoveryStrategyFactory(EurekaInstanceHelper eurekaInstanceHelper) {
        this.eurekaInstanceHelper = eurekaInstanceHelper;
    }

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
    public DiscoveryStrategy newDiscoveryStrategy(DiscoveryNode discoveryNode, ILogger logger, Map<String, Comparable> properties) {
        return new EurekaHazelcastDiscoveryStrategy(logger, properties, eurekaInstanceHelper);
    }

    /**
     * Returns the configuration properties supported by this discovery strategy.
     * Currently, no additional properties are required as the EurekaInstanceHelper
     * is passed through constructor injection.
     *
     * @return an empty collection (no additional properties required)
     */
    @Override
    public Collection<PropertyDefinition> getConfigurationProperties() {
        return Collections.emptyList();
    }
}
