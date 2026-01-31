package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.CacheConfiguration.HAZELCAST_MEMBER_TYPE_CLIENT;
import static de.tum.cit.aet.artemis.core.config.CacheConfiguration.HAZELCAST_MEMBER_TYPE_KEY;
import static de.tum.cit.aet.artemis.core.config.CacheConfiguration.HAZELCAST_MEMBER_TYPE_MEMBER;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_TEST_BUILDAGENT;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.cloud.client.serviceregistry.ServiceRegistry;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
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
@Conditional(CoreOrHazelcastBuildAgent.class)
@Lazy
@Configuration
public class HazelcastConnection {

    private static final Logger log = LoggerFactory.getLogger(HazelcastConnection.class);

    // the discovery service client that connects against the registration (jhipster registry) so that multiple server nodes can find each other to synchronize using Hazelcast
    private final DiscoveryClient discoveryClient;

    // the service registry, in our current deployment this is the jhipster registry which offers a Eureka Server under the hood
    private final Optional<Registration> registration;

    // the service registry used to trigger immediate re-registration when metadata changes
    private final Optional<ServiceRegistry<Registration>> serviceRegistry;

    private final Environment env;

    @Value("${spring.hazelcast.port:5701}")
    private int hazelcastPort;

    @Value("${spring.jpa.properties.hibernate.cache.hazelcast.instance_name}")
    private String instanceName;

    public HazelcastConnection(DiscoveryClient discoveryClient, Optional<Registration> registration, Optional<ServiceRegistry<Registration>> serviceRegistry, Environment env) {
        this.discoveryClient = discoveryClient;
        this.registration = registration;
        this.serviceRegistry = serviceRegistry;
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
     * This method is skipped when running as a Hazelcast client (build agent client mode),
     * as clients manage their own connections to the cluster.
     *
     * <p>
     * Called once after this bean (HazelcastConnection) has been instantiated
     */
    @PostConstruct
    private void connectHazelcast() {
        // Skip connection logic for Hazelcast clients - they manage their own connections
        if (isRunningAsClient()) {
            log.debug("Running as Hazelcast client, skipping cluster member connection logic");
            return;
        }

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
        if (isRunningAsClient()) {
            return;
        }

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
        }).collect(Collectors.toSet());

        var instances = discoveryClient.getInstances(registration.get().getServiceId());

        // Build set of registry member addresses (normalized for comparison)
        Set<String> registryMemberAddresses = new HashSet<>();
        for (ServiceInstance instance : instances) {
            registryMemberAddresses.add(normalizeHost(instance.getHost()));
        }

        log.debug("Current {} Registry members: {}", instances.size(), registryMemberAddresses);
        log.debug("Current {} Hazelcast members: {}", hazelcastMemberAddresses.size(), hazelcastMemberAddresses);

        // Check for members in registry but not in Hazelcast (need to add)
        for (ServiceInstance instance : instances) {
            var instanceHostClean = normalizeHost(instance.getHost());
            if (!hazelcastMemberAddresses.contains(instanceHostClean)) {
                addHazelcastClusterMember(instance, hazelcastInstance.getConfig());
            }
        }

        // Check for members in Hazelcast but not in registry (potentially stale/zombie members)
        // This can indicate a member that crashed without proper deregistration
        for (String hazelcastMember : hazelcastMemberAddresses) {
            if (!"unknown".equals(hazelcastMember) && !registryMemberAddresses.contains(hazelcastMember)) {
                // Don't log warning for own address
                String ownHost = normalizeHost(registration.get().getHost());
                if (!hazelcastMember.equals(ownHost)) {
                    log.warn("Hazelcast member {} is not registered in service registry - may be a stale/zombie member. "
                            + "If this persists, the member may have crashed without proper deregistration.", hazelcastMember);
                }
            }
        }
    }

    /**
     * Adds a given service instance as a TCP/IP cluster member to the Hazelcast configuration.
     * Extracts the Hazelcast port and host from instance metadata (or uses fallbacks) and
     * constructs the full address of the peer node for inclusion in the cluster.
     *
     * <p>
     * Uses the {@code hazelcast.host} metadata if available, which contains the actual
     * Hazelcast bind address. Falls back to the Eureka registration host if not set.
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
        // Prefer hazelcast.host from metadata (the actual Hazelcast bind address)
        String host = instance.getMetadata().get("hazelcast.host");
        if (host == null || host.isEmpty()) {
            host = instance.getHost();
        }
        // Normalize the host to remove brackets that Eureka adds for IPv6 addresses
        host = normalizeHost(host);
        var clusterMemberAddress = formatAddressForHazelcast(host, clusterMemberPort);
        log.info("Adding Hazelcast cluster member {}", clusterMemberAddress);
        config.getNetworkConfig().getJoin().getTcpIpConfig().addMember(clusterMemberAddress);
    }

    /**
     * Checks if the current instance is running as a Hazelcast client.
     * Build agents (without the core profile) always run as clients to isolate the core cluster
     * from build agent failures and eliminate heartbeat overhead.
     * Test profiles are excluded since build agent tests create a local Hazelcast instance.
     *
     * @return true if running as a Hazelcast client, false otherwise
     */
    private boolean isRunningAsClient() {
        return env.acceptsProfiles(Profiles.of(PROFILE_BUILDAGENT)) && !env.acceptsProfiles(Profiles.of(PROFILE_CORE))
                && !env.acceptsProfiles(Profiles.of(PROFILE_TEST_BUILDAGENT));
    }

    /**
     * Discovers core node (cluster member) addresses from the service registry.
     * This is used by build agents in client mode to find core nodes to connect to.
     *
     * <p>
     * Filters to only include cluster members by checking the {@code hazelcast.member-type} metadata.
     * Includes instances without this metadata for backwards compatibility with older deployments.
     *
     * @return list of core node addresses in "host:port" format, empty if no core nodes found
     */
    public List<String> discoverCoreNodeAddresses() {
        if (registration.isEmpty()) {
            log.warn("Service registry not available for auto-discovery of core nodes.");
            return List.of();
        }

        String serviceId = registration.get().getServiceId();
        log.info("Auto-discovering core nodes from service registry for serviceId '{}'", serviceId);
        List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);

        log.info("Found {} total instances in service registry for serviceId '{}'", instances.size(), serviceId);
        for (ServiceInstance instance : instances) {
            String memberType = instance.getMetadata().get(HAZELCAST_MEMBER_TYPE_KEY);
            String hazelcastHost = instance.getMetadata().get("hazelcast.host");
            log.info("Instance: host={}, hazelcast.host={}, port={}, member-type={}, isCurrentInstance={}", instance.getHost(), hazelcastHost, instance.getPort(), memberType,
                    isCurrentInstance(instance));
        }

        List<String> coreNodes = instances.stream()
                // Filter to only include cluster members (not clients)
                .filter(this::isClusterMember)
                // Exclude the current instance
                .filter(instance -> !isCurrentInstance(instance)).map(this::formatInstanceAddress).toList();

        if (!coreNodes.isEmpty()) {
            log.info("Discovered {} core node(s) from service registry: {}", coreNodes.size(), coreNodes);
        }
        else {
            log.warn("No core nodes found in service registry. Ensure core nodes are running and registered with hazelcast.member-type=member metadata.");
        }

        return coreNodes;
    }

    /**
     * Checks if a service instance is a Hazelcast cluster member (not a client).
     *
     * @param instance the service instance to check
     * @return true if the instance is a cluster member
     */
    private boolean isClusterMember(ServiceInstance instance) {
        String memberType = instance.getMetadata().get(HAZELCAST_MEMBER_TYPE_KEY);
        // Include instances explicitly marked as members, or not marked at all (legacy/compatibility)
        return memberType == null || HAZELCAST_MEMBER_TYPE_MEMBER.equals(memberType);
    }

    /**
     * Checks if the given service instance is the current instance.
     *
     * @param instance the service instance to check
     * @return true if this is the current instance
     */
    private boolean isCurrentInstance(ServiceInstance instance) {
        if (registration.isEmpty()) {
            return false;
        }
        String ownHost = normalizeHost(registration.get().getHost());
        String instanceHost = normalizeHost(instance.getHost());
        return ownHost.equals(instanceHost) && registration.get().getPort() == instance.getPort();
    }

    /**
     * Formats a service instance address as "host:port" for Hazelcast connection.
     * <p>
     * Uses the {@code hazelcast.host} metadata if available, which contains the actual
     * Hazelcast bind address. Falls back to the Eureka registration host if not set.
     * This is important because the Eureka host may differ from the Hazelcast bind address
     * (e.g., when Hazelcast is bound to a specific network interface).
     *
     * @param instance the service instance
     * @return the formatted address string
     */
    private String formatInstanceAddress(ServiceInstance instance) {
        // Prefer hazelcast.host from metadata (the actual Hazelcast bind address)
        // over instance.getHost() (which is the Eureka registration host)
        String host = instance.getMetadata().get("hazelcast.host");
        if (host == null || host.isEmpty()) {
            // Fall back to Eureka host for compatibility with older instances
            host = instance.getHost();
        }
        // Normalize the host to remove brackets that Eureka adds for IPv6 addresses
        host = normalizeHost(host);
        String port = instance.getMetadata().getOrDefault("hazelcast.port", String.valueOf(hazelcastPort));
        return formatAddressForHazelcast(host, port);
    }

    /**
     * Removes brackets from a host string if present.
     * Eureka/Spring Cloud stores IPv6 addresses with brackets (URI format per RFC 3986),
     * but we need the raw IP address for comparison and formatting operations.
     *
     * <p>
     * <strong>Examples:</strong>
     * <ul>
     * <li>{@code "[fcfe:0:0:0:0:0:a:1]"} → {@code "fcfe:0:0:0:0:0:a:1"} (IPv6 with brackets)</li>
     * <li>{@code "[::1]"} → {@code "::1"} (IPv6 localhost with brackets)</li>
     * <li>{@code "192.168.1.1"} → {@code "192.168.1.1"} (IPv4 unchanged)</li>
     * <li>{@code "fcfe:0:0:0:0:0:a:1"} → {@code "fcfe:0:0:0:0:0:a:1"} (IPv6 without brackets unchanged)</li>
     * <li>{@code null} → {@code null}</li>
     * </ul>
     *
     * @param host the host string, possibly with brackets (e.g., "[::1]" or "192.168.1.1")
     * @return the host without brackets (e.g., "::1" or "192.168.1.1"), or null if input is null
     */
    String normalizeHost(String host) {
        if (host == null) {
            return null;
        }
        // Remove URI-style brackets that Eureka adds for IPv6 addresses
        if (host.startsWith("[") && host.endsWith("]")) {
            return host.substring(1, host.length() - 1);
        }
        return host;
    }

    /**
     * Formats a host and port for Hazelcast connection.
     * IPv6 addresses must be wrapped in brackets: "[ipv6]:port", IPv4 is just "ipv4:port".
     * This follows RFC 3986 URI syntax for IPv6 literal addresses, which requires brackets
     * around IPv6 addresses to distinguish the port separator colon from IPv6 address colons.
     *
     * <p>
     * <strong>Examples:</strong>
     * <ul>
     * <li>{@code ("192.168.1.1", "5701")} → {@code "192.168.1.1:5701"} (IPv4)</li>
     * <li>{@code ("10.0.0.1", "5701")} → {@code "10.0.0.1:5701"} (IPv4 private)</li>
     * <li>{@code ("fcfe:0:0:0:0:0:a:1", "5701")} → {@code "[fcfe:0:0:0:0:0:a:1]:5701"} (IPv6 full)</li>
     * <li>{@code ("::1", "5701")} → {@code "[::1]:5701"} (IPv6 localhost)</li>
     * <li>{@code ("2001:db8::1", "5701")} → {@code "[2001:db8::1]:5701"} (IPv6 compressed)</li>
     * </ul>
     *
     * <p>
     * <strong>Important:</strong> The host parameter must be normalized (without brackets) before
     * calling this method. Use {@link #normalizeHost(String)} to remove brackets from Eureka-provided hosts.
     *
     * @param host the normalized host without brackets (use {@link #normalizeHost(String)} first)
     * @param port the port number as a string
     * @return the formatted address string suitable for Hazelcast TCP/IP configuration
     * @throws NullPointerException if host is null
     */
    String formatAddressForHazelcast(String host, String port) {
        // IPv6 addresses contain colons, so we need to wrap them in brackets
        // to distinguish the port separator from the IPv6 address colons
        if (host.contains(":")) {
            return "[" + host + "]:" + port;
        }
        return host + ":" + port;
    }

    /**
     * Marks this instance as a Hazelcast client in the service registry.
     * This allows other instances to identify it as a client rather than a cluster member.
     *
     * <p>
     * The metadata change is immediately propagated to the Eureka server by triggering
     * a re-registration. This ensures other instances can identify this node as a client
     * without waiting for the next heartbeat (default interval: 30 seconds).
     *
     * <p>
     * If the ServiceRegistry is not available (e.g., in test environments), the metadata
     * change will still be made locally and propagated on the next heartbeat.
     */
    public void registerAsClient() {
        if (registration.isPresent()) {
            registration.get().getMetadata().put(HAZELCAST_MEMBER_TYPE_KEY, HAZELCAST_MEMBER_TYPE_CLIENT);
            log.info("Marked this instance as Hazelcast client in service registry metadata");

            // Trigger immediate re-registration to propagate metadata change
            if (serviceRegistry.isPresent()) {
                try {
                    serviceRegistry.get().register(registration.get());
                    log.info("Triggered immediate Eureka re-registration for Hazelcast client metadata");
                }
                catch (Exception e) {
                    log.warn("Failed to trigger immediate Eureka re-registration: {}. Metadata will propagate on next heartbeat.", e.getMessage());
                }
            }
            else {
                log.debug("ServiceRegistry not available - metadata will propagate on next Eureka heartbeat");
            }
        }
    }
}
