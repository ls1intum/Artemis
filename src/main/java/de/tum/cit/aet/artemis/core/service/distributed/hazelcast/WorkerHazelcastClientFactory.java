package de.tum.cit.aet.artemis.core.service.distributed.hazelcast;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_AIWORKER;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.ClientConnectionStrategyConfig;
import com.hazelcast.client.config.RoutingMode;
import com.hazelcast.config.DiscoveryConfig;
import com.hazelcast.config.DiscoveryStrategyConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.spring.context.SpringManagedContext;

import de.tum.cit.aet.artemis.core.config.EurekaHazelcastDiscoveryStrategy;
import de.tum.cit.aet.artemis.core.config.EurekaHazelcastDiscoveryStrategyFactory;
import de.tum.cit.aet.artemis.core.config.EurekaInstanceHelper;
import de.tum.cit.aet.artemis.core.config.HazelcastPathSerializer;
import de.tum.cit.aet.artemis.core.service.distributed.DistributedDataTypeFilter;

/** Configures the same discovery, serialization and reconnect behavior in universal and dedicated worker executables. */
public record WorkerHazelcastClientFactory(ApplicationContext applicationContext, EurekaInstanceHelper eurekaInstanceHelper, Environment env, String instanceName,
        boolean hazelcastLocalInstances, List<String> additionalAllowedTypes) {

    private static final Logger log = LoggerFactory.getLogger(WorkerHazelcastClientFactory.class);

    /**
     * Creates a Hazelcast client for build agents to connect to the core cluster.
     *
     * <p>
     * <strong>Why Clients Instead of Members:</strong>
     * Build agents connecting as Hazelcast clients (rather than cluster members) provides
     * significant architectural benefits:
     *
     * <ul>
     * <li><strong>Cluster Isolation:</strong> Client failures don't affect cluster membership.
     * When a build agent crashes or is scaled down, the core cluster doesn't need to rebalance
     * partitions or handle member departure events.</li>
     *
     * <li><strong>Reduced Heartbeat Overhead:</strong> Cluster members send heartbeats to
     * all other members (O(n²) messages). Clients only maintain connections to cluster members,
     * dramatically reducing network traffic with many build agents.</li>
     *
     * <li><strong>Simplified Scaling:</strong> Build agents can be added/removed dynamically
     * (auto-scaling, spot instances) without any impact on the core cluster's partition
     * distribution or consensus.</li>
     *
     * <li><strong>Data Durability:</strong> All data partitions are owned by stable core nodes.
     * Even if all build agents disconnect, no data is lost.</li>
     * </ul>
     *
     * <p>
     * <strong>Discovery Flow:</strong>
     * <ol>
     * <li>Client uses {@link EurekaHazelcastDiscoveryStrategy} to query Eureka for core nodes</li>
     * <li>Strategy filters for instances with {@code hazelcast.member-type=member} metadata</li>
     * <li>Client connects to discovered core nodes</li>
     * <li>After connection, registers itself as a client in Eureka (via {@link EurekaInstanceHelper})</li>
     * </ol>
     *
     * @return a HazelcastInstance configured as a client
     */
    public HazelcastInstance createHazelcastClient() {
        ClientConfig clientConfig = new ClientConfig();
        // Use Spring's classloader to ensure all application classes are available for deserialization
        clientConfig.setClassLoader(applicationContext.getClassLoader());
        // Enable Spring dependency injection in Hazelcast-managed objects
        clientConfig.setManagedContext(new SpringManagedContext(applicationContext));
        configureClientIdentity(clientConfig);
        configureClientDiscovery(clientConfig);
        configureClientConnectionStrategy(clientConfig);
        configureClientHeartbeat(clientConfig);
        configureClientNetworking(clientConfig);
        clientConfig.getSerializationConfig().addSerializerConfig(HazelcastPathSerializer.configuration());
        HazelcastTypeFilter.apply(clientConfig.getSerializationConfig(), DistributedDataTypeFilter.withAdditional(additionalAllowedTypes));

        log.info("Creating Hazelcast client with Eureka-based dynamic discovery");

        return HazelcastClient.newHazelcastClient(clientConfig);
    }

    /**
     * Configures client identity and cluster name.
     *
     * <p>
     * AI Workers use their required worker id with an {@code aiworker-} prefix. Build agents retain their short name,
     * or the instance name with a {@code -client} suffix when no short name is configured.
     *
     * <p>
     * <strong>Cluster Name:</strong> In production mode ({@code spring.hazelcast.localInstances=false}),
     * the cluster name is set to "prod" to match the core cluster configuration. Clients must
     * use the same cluster name as the cluster they're connecting to.
     *
     * @param clientConfig the client configuration to modify
     */
    private void configureClientIdentity(ClientConfig clientConfig) {
        if (env.acceptsProfiles(Profiles.of(PROFILE_AIWORKER))) {
            clientConfig.setInstanceName("aiworker-" + env.getRequiredProperty("artemis.aiworker.id"));
        }
        else {
            String buildAgentShortName = env.getProperty("artemis.continuous-integration.build-agent.short-name");
            if (buildAgentShortName == null || buildAgentShortName.trim().isEmpty()) {
                buildAgentShortName = instanceName + "-client";
            }
            clientConfig.setInstanceName(buildAgentShortName);
        }

        if (!hazelcastLocalInstances) {
            clientConfig.setClusterName("prod");
        }
    }

    /**
     * Configures Eureka-based discovery for finding core cluster nodes.
     *
     * <p>
     * <strong>Discovery Strategy Pattern:</strong> Hazelcast's Discovery SPI allows pluggable
     * discovery mechanisms. We use a custom {@link EurekaHazelcastDiscoveryStrategy} that:
     * <ol>
     * <li>Queries Eureka for all Artemis instances</li>
     * <li>Filters for instances marked as Hazelcast members (not clients)</li>
     * <li>Extracts Hazelcast host/port from instance metadata</li>
     * <li>Returns addresses for Hazelcast to connect to</li>
     * </ol>
     *
     * <p>
     * <strong>Why Custom Discovery:</strong> Hazelcast's built-in Eureka plugin has limitations:
     * <ul>
     * <li>Doesn't support filtering by metadata (member vs client)</li>
     * <li>Uses Eureka's registered port, not Hazelcast's separate port</li>
     * <li>Requires different configuration for members and clients</li>
     * </ul>
     * Our custom strategy uses the same {@link EurekaInstanceHelper} as runtime cluster
     * management, ensuring consistent discovery behavior.
     *
     * @param clientConfig the client configuration to modify
     */
    private void configureClientDiscovery(ClientConfig clientConfig) {
        var discoveryStrategyFactory = new EurekaHazelcastDiscoveryStrategyFactory(eurekaInstanceHelper);
        DiscoveryStrategyConfig discoveryStrategyConfig = new DiscoveryStrategyConfig(discoveryStrategyFactory);
        DiscoveryConfig discoveryConfig = clientConfig.getNetworkConfig().getDiscoveryConfig();
        discoveryConfig.addDiscoveryStrategyConfig(discoveryStrategyConfig);

        clientConfig.setProperty("hazelcast.discovery.enabled", "true");
    }

    /**
     * Configures connection strategy for resilience and independent startup.
     *
     * <p>
     * <strong>Async Start ({@code asyncStart=true}):</strong> The client starts immediately
     * and connects to the cluster in the background. This is critical because:
     * <ul>
     * <li>Build agents can start even when core nodes are temporarily unavailable</li>
     * <li>Spring context initialization isn't blocked by Hazelcast connection</li>
     * <li>Health checks and readiness probes can run before cluster connection</li>
     * </ul>
     *
     * <p>
     * <strong>Reconnect Mode ({@code reconnectMode=ON}):</strong> If the connection to all
     * cluster members is lost, the client automatically attempts to reconnect. This handles
     * transient network issues and cluster rolling restarts.
     *
     * <p>
     * <strong>Connection Retry Configuration:</strong>
     * <ul>
     * <li><strong>Initial backoff (1s):</strong> First retry delay - quick initial retry
     * for transient issues</li>
     * <li><strong>Max backoff (30s):</strong> Maximum delay between retries - prevents
     * overwhelming the cluster during extended outages</li>
     * <li><strong>Multiplier (1.5):</strong> Exponential backoff factor - 1s, 1.5s, 2.25s, etc.</li>
     * <li><strong>Cluster connect timeout (-1):</strong> Infinite timeout - never give up
     * trying to connect (build agents should keep trying until core is available)</li>
     * <li><strong>Jitter (0.2):</strong> 20% randomization to prevent thundering herd when
     * multiple clients reconnect simultaneously</li>
     * </ul>
     *
     * @param clientConfig the client configuration to modify
     */
    private void configureClientConnectionStrategy(ClientConfig clientConfig) {
        clientConfig.getConnectionStrategyConfig().setAsyncStart(true).setReconnectMode(ClientConnectionStrategyConfig.ReconnectMode.ON);

        clientConfig.getConnectionStrategyConfig().getConnectionRetryConfig().setInitialBackoffMillis(1000).setMaxBackoffMillis(30000).setMultiplier(1.5)
                .setClusterConnectTimeoutMillis(-1).setJitter(0.2);
    }

    /**
     * Configures client heartbeat settings aligned with cluster member settings.
     *
     * <p>
     * <strong>Heartbeat Interval (5s):</strong> Clients send heartbeats to the cluster
     * every 5 seconds. This matches the cluster member heartbeat interval, ensuring
     * consistent failure detection timing across the system.
     *
     * <p>
     * <strong>Heartbeat Timeout (15s):</strong> If no response is received within 15 seconds,
     * the client considers the connection lost and attempts reconnection. This provides:
     * <ul>
     * <li>~15 second detection time (matching cluster member settings)</li>
     * <li>Tolerance for 2 missed heartbeats before declaring failure</li>
     * <li>Reasonable tolerance for GC pauses and network jitter</li>
     * </ul>
     *
     * <p>
     * <strong>Alignment Rationale:</strong> Using the same heartbeat timing as cluster members
     * ensures that client disconnection and member failure are detected in approximately
     * the same timeframe, providing predictable behavior for operations that span both.
     *
     * @param clientConfig the client configuration to modify
     */
    private void configureClientHeartbeat(ClientConfig clientConfig) {
        clientConfig.setProperty("hazelcast.client.heartbeat.interval", "5000");
        clientConfig.setProperty("hazelcast.client.heartbeat.timeout", "15000");
    }

    /**
     * Configures client networking options.
     *
     * <p>
     * <strong>Connection Timeout (10s):</strong> Maximum time to wait when establishing
     * a new connection to a cluster member. 10 seconds is generous enough to handle
     * slow networks while preventing indefinite blocking.
     *
     * <p>
     * <strong>Routing Mode (ALL_MEMBERS):</strong> The client maintains connections to
     * all cluster members, not just a subset. This provides:
     * <ul>
     * <li>Direct access to any partition owner (no forwarding needed)</li>
     * <li>Faster failover when a member fails (already connected to others)</li>
     * <li>Load distribution across all members</li>
     * </ul>
     * The trade-off is more connections, but with a small core cluster (typically 2-5 nodes),
     * this is acceptable.
     *
     * @param clientConfig the client configuration to modify
     */
    private void configureClientNetworking(ClientConfig clientConfig) {
        clientConfig.getNetworkConfig().setConnectionTimeout(10000).getClusterRoutingConfig().setRoutingMode(RoutingMode.ALL_MEMBERS);
    }
}
