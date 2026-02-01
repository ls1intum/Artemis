package de.tum.cit.aet.artemis.core.config;

import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.scheduling.annotation.Scheduled;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;

/**
 * Manages Hazelcast cluster membership by establishing and maintaining connections between
 * cluster members across different instances of the application using a service discovery
 * mechanism (e.g., JHipster Registry with Eureka).
 *
 * <p>
 * This class ensures dynamic membership configuration for the Hazelcast cluster based on registered
 * service instances, enabling inter-node communication and synchronization across distributed deployments.
 *
 * <p>
 * <strong>Separation of Concerns:</strong> This class is solely responsible for establishing runtime
 * connectivity between Hazelcast nodes after full application startup. The actual Hazelcast configuration (e.g., cache regions,
 * eviction policies, serialization settings) is defined separately in {@link HazelcastConfiguration}.
 *
 * <p>
 * The class avoids connection logic in test environments and handles potential split-brain scenarios
 * by periodically verifying and initiating connections to all expected cluster members.
 */
@Conditional(CoreOrHazelcastBuildAgent.class)
@Lazy
@Configuration
public class HazelcastClusterManager {

    private static final Logger log = LoggerFactory.getLogger(HazelcastClusterManager.class);

    private final EurekaInstanceHelper eurekaInstanceHelper;

    private final Environment env;

    @Value("${spring.jpa.properties.hibernate.cache.hazelcast.instance_name}")
    private String instanceName;

    public HazelcastClusterManager(EurekaInstanceHelper eurekaInstanceHelper, Environment env) {
        this.eurekaInstanceHelper = eurekaInstanceHelper;
        this.env = env;
    }

    /**
     * Connects the local Hazelcast instance to other known service instances after the application
     * has fully started. This enables dynamic TCP/IP cluster membership by resolving peers via
     * the service registry (e.g., Eureka).
     *
     * <p>
     * Executed only if a service registration is available, indicating a clustered environment.
     * This method uses {@link EurekaInstanceHelper} to retrieve other instances of the same service
     * and registers them in the Hazelcast configuration for joining the cluster.
     *
     * <p>
     * This method is skipped when running as a Hazelcast client (build agent client mode),
     * as clients manage their own connections to the cluster.
     *
     * <p>
     * Called once after this bean (HazelcastClusterManager) has been instantiated
     */
    @PostConstruct
    private void connectHazelcast() {
        // Skip connection logic for Hazelcast clients - they manage their own connections
        if (eurekaInstanceHelper.isRunningAsClient()) {
            log.debug("Running as Hazelcast client, skipping cluster member connection logic");
            return;
        }

        if (eurekaInstanceHelper.getServiceId().isEmpty()) {
            // If there is no registration, we are not running in a clustered environment and cannot connect Hazelcast nodes.
            return;
        }
        var hazelcastInstance = Hazelcast.getHazelcastInstanceByName(instanceName);
        if (hazelcastInstance == null) {
            log.error("Hazelcast instance not found, cannot connect to cluster members");
            return;
        }
        var config = hazelcastInstance.getConfig();

        var instances = eurekaInstanceHelper.getServiceInstances();
        // Filter to only include cluster members (not clients like build agents)
        var clusterMembers = instances.stream().filter(eurekaInstanceHelper::isClusterMember).toList();
        log.info("Connecting Hazelcast instance '{}' to {} cluster members (filtered from {} total instances)", instanceName, clusterMembers.size(), instances.size());
        for (ServiceInstance instance : clusterMembers) {
            addHazelcastClusterMember(instance, config);
        }
    }

    /**
     * This scheduled task regularly checks if all members of the Hazelcast cluster are connected to each other.
     * This is one counter measure against a split cluster.
     * It also logs warnings for potential stale members (in Hazelcast but not in registry).
     *
     * <p>
     * This method is skipped when running as a Hazelcast client (build agent client mode),
     * as clients do not participate in cluster membership.
     */
    @Scheduled(fixedDelay = 10, initialDelay = 10, timeUnit = TimeUnit.SECONDS)
    public void connectToAllMembers() {
        // Skip connection logic for Hazelcast clients - they don't participate in cluster membership
        if (eurekaInstanceHelper.isRunningAsClient()) {
            return;
        }

        if (eurekaInstanceHelper.getServiceId().isEmpty() || env.acceptsProfiles(Profiles.of(SPRING_PROFILE_TEST))) {
            return;
        }
        var hazelcastInstance = Hazelcast.getHazelcastInstanceByName(instanceName);
        if (hazelcastInstance == null) {
            log.error("Hazelcast instance not found, cannot connect to cluster members");
            return;
        }

        var hazelcastMemberAddresses = hazelcastInstance.getCluster().getMembers().stream()
                .map(member -> eurekaInstanceHelper.formatAddressForHazelcast(member.getAddress().getHost(), String.valueOf(member.getAddress().getPort())))
                .collect(Collectors.toSet());

        var instances = eurekaInstanceHelper.getServiceInstances();
        // Filter to only include cluster members (not clients like build agents)
        var clusterMemberInstances = instances.stream().filter(eurekaInstanceHelper::isClusterMember).toList();

        // Build set of registry member addresses using the Hazelcast host/port metadata for comparison
        Set<String> registryMemberAddresses = new HashSet<>();
        for (ServiceInstance instance : clusterMemberInstances) {
            registryMemberAddresses.add(eurekaInstanceHelper.formatInstanceAddress(instance));
        }

        log.debug("Current {} Registry cluster members: {}", clusterMemberInstances.size(), registryMemberAddresses);
        log.debug("Current {} Hazelcast members: {}", hazelcastMemberAddresses.size(), hazelcastMemberAddresses);

        // Check for members in registry but not in Hazelcast (need to add)
        for (ServiceInstance instance : clusterMemberInstances) {
            var instanceHazelcastAddress = eurekaInstanceHelper.formatInstanceAddress(instance);
            if (!hazelcastMemberAddresses.contains(instanceHazelcastAddress)) {
                addHazelcastClusterMember(instance, hazelcastInstance.getConfig());
            }
        }

        // Check for members in Hazelcast but not in registry (potentially stale/zombie members)
        // This can indicate a member that crashed without proper deregistration
        for (String hazelcastMember : hazelcastMemberAddresses) {
            if (!registryMemberAddresses.contains(hazelcastMember)) {
                // Don't log warning for own address - get own host from first service instance that matches current port
                var currentInstance = instances.stream().filter(eurekaInstanceHelper::isCurrentInstance).findFirst();
                if (currentInstance.isPresent()) {
                    String ownAddress = eurekaInstanceHelper.formatInstanceAddress(currentInstance.get());
                    if (!hazelcastMember.equals(ownAddress)) {
                        log.warn("Hazelcast member {} is not registered in service registry - may be a stale/zombie member. "
                                + "If this persists, the member may have crashed without proper deregistration.", hazelcastMember);
                    }
                }
            }
        }
    }

    /**
     * Adds a given service instance as a TCP/IP cluster member to the Hazelcast configuration.
     * Uses {@link EurekaInstanceHelper#formatInstanceAddress(ServiceInstance)} to construct
     * the full address of the peer node for inclusion in the cluster.
     *
     * <p>
     * This method is invoked during initial cluster formation to ensure the Hazelcast
     * instance knows about its peers, enabling them to form a single logical cluster.
     *
     * @param instance the service instance to be added to the Hazelcast cluster
     * @param config   the Hazelcast configuration to which the new member will be added
     */
    private void addHazelcastClusterMember(ServiceInstance instance, Config config) {
        var clusterMemberAddress = eurekaInstanceHelper.formatInstanceAddress(instance);
        log.info("Adding Hazelcast cluster member {}", clusterMemberAddress);
        config.getNetworkConfig().getJoin().getTcpIpConfig().addMember(clusterMemberAddress);
    }
}
