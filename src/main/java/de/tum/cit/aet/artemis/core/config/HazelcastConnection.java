package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.net.UnknownHostException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.scheduling.annotation.Scheduled;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;

/**
 * Establishes and maintains connections between Hazelcast cluster members across different instances
 * of the application using a service discovery mechanism (e.g., JHipster Registry with Eureka).
 *
 * <p>
 * This class ensures dynamic membership configuration for the Hazelcast cluster based on registered
 * service instances, enabling inter-node communication and synchronization across distributed deployments.
 *
 * <p>
 * <strong>Separation of Concerns:</strong> This class is solely responsible for establishing runtime
 * connectivity between Hazelcast nodes after full application startup. The actual Hazelcast configuration (e.g., cache regions,
 * eviction policies, serialization settings) is defined separately in {@link CacheConfiguration}.
 *
 * <p>
 * The class avoids connection logic in test environments and handles potential split-brain scenarios
 * by periodically verifying and initiating connections to all expected cluster members.
 */
@Profile({ PROFILE_BUILDAGENT, PROFILE_CORE })
@Lazy
@Configuration
public class HazelcastConnection {

    private static final Logger log = LoggerFactory.getLogger(HazelcastConnection.class);

    // the discovery service client that connects against the registration (jhipster registry) so that multiple server nodes can find each other to synchronize using Hazelcast
    private final DiscoveryClient discoveryClient;

    // the service registry, in our current deployment this is the jhipster registry which offers a Eureka Server under the hood
    private final Optional<Registration> registration;

    private final Environment env;

    @Value("${spring.hazelcast.port:5701}")
    private int hazelcastPort;

    @Value("${spring.jpa.properties.hibernate.cache.hazelcast.instance_name}")
    private String instanceName;

    public HazelcastConnection(DiscoveryClient discoveryClient, Optional<Registration> registration, Environment env) {
        this.discoveryClient = discoveryClient;
        this.registration = registration;
        this.env = env;
    }

    /**
     * Connects the local Hazelcast instance to other known service instances after the application
     * has fully started. This enables dynamic TCP/IP cluster membership by resolving peers via
     * the service registry (e.g., Eureka).
     *
     * <p>
     * Executed only if a {@link Registration} is available, indicating a clustered environment.
     * This method uses the {@link DiscoveryClient} to retrieve other instances of the same service
     * and registers them in the Hazelcast configuration for joining the cluster.
     *
     * <p>
     * Called once after this bean (HazelcastConnection) has been instantiated
     */
    @PostConstruct
    private void connectHazelcast() {
        if (registration.isEmpty()) {
            // If there is no registration, we are not running in a clustered environment and cannot connect Hazelcast nodes.
            return;
        }
        var hazelcastInstance = Hazelcast.getHazelcastInstanceByName(instanceName);
        if (hazelcastInstance == null) {
            log.error("Hazelcast instance not found, cannot connect to cluster members");
            return;
        }
        var config = hazelcastInstance.getConfig();

        String serviceId = registration.get().getServiceId();
        var instances = discoveryClient.getInstances(serviceId);
        log.info("Connecting Hazelcast instance '{}' to {} other cluster members", instanceName, instances.size());
        for (ServiceInstance instance : instances) {
            addHazelcastClusterMember(instance, config);
        }
    }

    /**
     * This scheduled task regularly checks if all members of the Hazelcast cluster are connected to each other.
     * This is one countermeasure to a split cluster.
     */
    @Scheduled(fixedRate = 2, initialDelay = 1, timeUnit = TimeUnit.MINUTES)
    public void connectToAllMembers() {
        if (registration.isEmpty() || env.acceptsProfiles(Profiles.of(SPRING_PROFILE_TEST))) {
            return;
        }
        var hazelcastInstance = Hazelcast.getHazelcastInstanceByName(instanceName);
        if (hazelcastInstance == null) {
            log.error("Hazelcast instance not found, cannot connect to cluster members");
            return;
        }

        var hazelcastMemberAddresses = hazelcastInstance.getCluster().getMembers().stream().map(member -> {
            try {
                return member.getAddress().getInetAddress().getHostAddress();
            }
            catch (UnknownHostException e) {
                return "unknown";
            }
        }).toList();

        var instances = discoveryClient.getInstances(registration.get().getServiceId());
        log.debug("Current {} Registry members: {}", instances.size(), instances.stream().map(ServiceInstance::getHost).toList());
        log.debug("Current {} Hazelcast members: {}", hazelcastMemberAddresses.size(), hazelcastMemberAddresses);

        for (ServiceInstance instance : instances) {
            // Workaround for IPv6 addresses, as they are enclosed in brackets
            var instanceHostClean = instance.getHost().replace("[", "").replace("]", "");
            if (hazelcastMemberAddresses.stream().noneMatch(member -> member.equals(instanceHostClean))) {
                addHazelcastClusterMember(instance, hazelcastInstance.getConfig());
            }
        }
    }

    /**
     * Adds a given service instance as a TCP/IP cluster member to the Hazelcast configuration.
     * Extracts the Hazelcast port from instance metadata (or uses a default fallback) and
     * constructs the full address of the peer node for inclusion in the cluster.
     *
     * <p>
     * This method is invoked during initial cluster formation to ensure the Hazelcast
     * instance knows about its peers, enabling them to form a single logical cluster.
     *
     * @param instance the service instance to be added to the Hazelcast cluster
     * @param config   the Hazelcast configuration to which the new member will be added
     */
    private void addHazelcastClusterMember(ServiceInstance instance, Config config) {
        var clusterMemberPort = instance.getMetadata().getOrDefault("hazelcast.port", String.valueOf(hazelcastPort));
        var clusterMemberAddress = instance.getHost() + ":" + clusterMemberPort; // Address where the other instance is expected
        log.info("Adding Hazelcast cluster member {}", clusterMemberAddress);
        config.getNetworkConfig().getJoin().getTcpIpConfig().addMember(clusterMemberAddress);
    }
}
