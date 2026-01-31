package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_TEST_BUILDAGENT;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.cloud.client.serviceregistry.ServiceRegistry;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Helper service for Eureka-based service instance discovery and metadata management.
 *
 * <p>
 * This class provides focused functionality for:
 * <ul>
 * <li>Discovering core node addresses for Hazelcast client connections</li>
 * <li>Managing Hazelcast-related metadata in the service registry</li>
 * <li>Utility methods for address formatting (IPv4/IPv6 handling)</li>
 * </ul>
 *
 * <p>
 * This class intentionally has no dependency on Hazelcast, making it usable by both:
 * <ul>
 * <li>{@link HazelcastConfiguration} - for creating Hazelcast clients with dynamic discovery</li>
 * <li>{@link HazelcastClusterManager} - for runtime cluster membership management</li>
 * </ul>
 *
 * <p>
 * <strong>Design rationale:</strong> By extracting discovery logic into this focused service,
 * we avoid a circular dependency between HazelcastConfiguration (which creates the Hazelcast instance)
 * and HazelcastClusterManager (which manages runtime connections).
 */
@Component
@Conditional(CoreOrHazelcastBuildAgent.class)
public class EurekaInstanceHelper {

    private static final Logger log = LoggerFactory.getLogger(EurekaInstanceHelper.class);

    /**
     * Metadata key used to identify the Hazelcast member type in the service registry.
     * Core nodes are marked as "member", build agent clients are marked as "client".
     */
    public static final String HAZELCAST_MEMBER_TYPE_KEY = "hazelcast.member-type";

    public static final String HAZELCAST_MEMBER_TYPE_MEMBER = "member";

    public static final String HAZELCAST_MEMBER_TYPE_CLIENT = "client";

    private final DiscoveryClient discoveryClient;

    private final Optional<Registration> registration;

    private final Optional<ServiceRegistry<Registration>> serviceRegistry;

    private final Environment env;

    @Value("${spring.hazelcast.port:5701}")
    private int hazelcastPort;

    public EurekaInstanceHelper(DiscoveryClient discoveryClient, Optional<Registration> registration, Optional<ServiceRegistry<Registration>> serviceRegistry, Environment env) {
        this.discoveryClient = discoveryClient;
        this.registration = registration;
        this.serviceRegistry = serviceRegistry;
        this.env = env;
    }

    /**
     * Gets the service ID from the registration, if available.
     *
     * @return the service ID, or empty if no registration is available
     */
    public Optional<String> getServiceId() {
        return registration.map(Registration::getServiceId);
    }

    /**
     * Gets all service instances for the current service from the discovery client.
     *
     * @return list of service instances, empty list if no registration is available
     */
    public List<ServiceInstance> getServiceInstances() {
        if (registration.isEmpty()) {
            return List.of();
        }
        return discoveryClient.getInstances(registration.get().getServiceId());
    }

    /**
     * Checks if the current instance is running as a Hazelcast client.
     * Build agents (without the core profile) always run as clients to isolate the core cluster
     * from build agent failures and eliminate heartbeat overhead.
     * Test profiles are excluded since build agent tests create a local Hazelcast instance.
     *
     * @return true if running as a Hazelcast client, false otherwise
     */
    public boolean isRunningAsClient() {
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
    public boolean isClusterMember(ServiceInstance instance) {
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
    public boolean isCurrentInstance(ServiceInstance instance) {
        if (registration.isEmpty()) {
            return false;
        }
        String ownHost = normalizeHost(registration.get().getHost());
        String instanceHost = normalizeHost(instance.getHost());
        // Use Objects.equals for null-safe comparison (getHost() may return null)
        return Objects.equals(ownHost, instanceHost) && registration.get().getPort() == instance.getPort();
    }

    /**
     * Gets the Hazelcast host address for a service instance.
     * <p>
     * Uses the {@code hazelcast.host} metadata if available, which contains the actual
     * Hazelcast bind address. Falls back to the Eureka registration host if not set.
     * The returned host is normalized (brackets removed for IPv6).
     *
     * @param instance the service instance
     * @return the normalized Hazelcast host address
     */
    public String getHazelcastHost(ServiceInstance instance) {
        // Prefer hazelcast.host from metadata (the actual Hazelcast bind address)
        // over instance.getHost() (which is the Eureka registration host)
        String host = instance.getMetadata().get("hazelcast.host");
        if (host == null || host.isEmpty()) {
            // Fall back to Eureka host for compatibility with older instances
            host = instance.getHost();
        }
        // Normalize the host to remove brackets that Eureka adds for IPv6 addresses
        return normalizeHost(host);
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
    public String formatInstanceAddress(ServiceInstance instance) {
        String host = getHazelcastHost(instance);
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
    public String normalizeHost(String host) {
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
    public String formatAddressForHazelcast(String host, String port) {
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

    /**
     * Marks this instance as a Hazelcast cluster member in the service registry.
     * This allows build agents (Hazelcast clients) to identify core nodes for connection.
     *
     * <p>
     * The metadata change is immediately propagated to the Eureka server by triggering
     * a re-registration. This ensures build agents can identify core nodes without
     * waiting for the next heartbeat (default interval: 30 seconds).
     *
     * <p>
     * This method should be called after setting all Hazelcast-related metadata
     * (hazelcast.host, hazelcast.port) to ensure all metadata is propagated together.
     *
     * @param hazelcastHost the Hazelcast bind address to store in metadata
     * @param hazelcastPort the Hazelcast port to store in metadata
     */
    public void registerAsMember(String hazelcastHost, int hazelcastPort) {
        if (registration.isPresent()) {
            registration.get().getMetadata().put(HAZELCAST_MEMBER_TYPE_KEY, HAZELCAST_MEMBER_TYPE_MEMBER);
            registration.get().getMetadata().put("hazelcast.host", hazelcastHost);
            registration.get().getMetadata().put("hazelcast.port", String.valueOf(hazelcastPort));
            log.info("Marked this instance as Hazelcast cluster member with host={}, port={}", hazelcastHost, hazelcastPort);

            // Trigger immediate re-registration to propagate metadata change
            if (serviceRegistry.isPresent()) {
                try {
                    serviceRegistry.get().register(registration.get());
                    log.info("Triggered immediate Eureka re-registration for Hazelcast member metadata");
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

    /**
     * Gets the default Hazelcast port from configuration.
     *
     * @return the Hazelcast port
     */
    public int getHazelcastPort() {
        return hazelcastPort;
    }
}
