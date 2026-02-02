package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_TEST_BUILDAGENT;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_TEST_INDEPENDENT;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.ClientConnectionStrategyConfig;
import com.hazelcast.client.config.RoutingMode;
import com.hazelcast.config.Config;
import com.hazelcast.config.DiscoveryConfig;
import com.hazelcast.config.DiscoveryStrategyConfig;
import com.hazelcast.config.EvictionConfig;
import com.hazelcast.config.EvictionPolicy;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.MaxSizePolicy;
import com.hazelcast.config.MemberAttributeConfig;
import com.hazelcast.config.QueueConfig;
import com.hazelcast.config.SerializerConfig;
import com.hazelcast.config.SplitBrainProtectionConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.spi.properties.ClusterProperty;
import com.hazelcast.spring.cache.HazelcastCacheManager;
import com.hazelcast.spring.context.SpringManagedContext;

import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.programming.service.localci.LocalCIPriorityQueueComparator;
import tech.jhipster.config.JHipsterProperties;
import tech.jhipster.config.cache.PrefixedKeyGenerator;

/**
 * Configures and initializes the Hazelcast distributed data grid for Artemis.
 *
 * <p>
 * <strong>Overview:</strong> Hazelcast provides distributed caching, messaging, and data structures
 * that enable Artemis to scale horizontally across multiple nodes. This configuration class
 * handles the complexity of three distinct deployment modes, each with different requirements
 * for networking, membership, and data ownership.
 *
 * <h2>Deployment Modes</h2>
 *
 * <h3>1. Test Mode (Isolated Instances)</h3>
 * <p>
 * When running with the {@code test} Spring profile (excluding {@code test-independent}), each test
 * gets its own completely isolated Hazelcast instance. This prevents test interference when running
 * tests in parallel - a common source of flaky tests in distributed systems.
 * <ul>
 * <li><strong>Rationale:</strong> Without isolation, parallel tests would share cache state, leading
 * to unpredictable failures when one test modifies data another test expects</li>
 * <li><strong>Implementation:</strong> Random cluster name (UUID) and dynamically assigned port
 * ensure no two test instances can accidentally form a cluster</li>
 * </ul>
 *
 * <h3>2. Client Mode (Build Agents)</h3>
 * <p>
 * Build agents (with {@code buildagent} profile but without {@code core}) connect as Hazelcast
 * clients rather than full cluster members. This architectural choice provides several benefits:
 * <ul>
 * <li><strong>Cluster Stability:</strong> Build agent crashes don't trigger cluster rebalancing
 * or split-brain scenarios in the core cluster</li>
 * <li><strong>Reduced Overhead:</strong> Clients don't participate in cluster heartbeats or
 * partition ownership, reducing network traffic</li>
 * <li><strong>Simpler Scaling:</strong> Build agents can be added/removed dynamically without
 * affecting cluster membership</li>
 * <li><strong>Isolation:</strong> Core cluster data partitions are owned only by core nodes,
 * ensuring data durability even when all build agents are offline</li>
 * </ul>
 *
 * <h3>3. Cluster Member Mode (Core Nodes)</h3>
 * <p>
 * Core nodes (with {@code core} profile) participate as full Hazelcast cluster members. They:
 * <ul>
 * <li>Own data partitions and maintain backup copies</li>
 * <li>Participate in cluster consensus and leader election</li>
 * <li>Handle distributed cache operations and messaging</li>
 * </ul>
 *
 * <h2>Network Configuration Rationale</h2>
 * <p>
 * Hazelcast networking is configured to:
 * <ul>
 * <li><strong>Disable multicast:</strong> Multicast discovery is unreliable in cloud environments
 * and can lead to accidental cluster formation between unrelated instances</li>
 * <li><strong>Use TCP/IP with Eureka discovery:</strong> Service registry integration ensures
 * nodes can find each other dynamically without static IP configuration</li>
 * <li><strong>Bind to specific interfaces:</strong> Prevents Hazelcast from binding to unexpected
 * interfaces (e.g., Docker bridge networks) which causes connectivity issues</li>
 * </ul>
 *
 * <h2>Stability Configuration Rationale</h2>
 * <p>
 * The cluster stability settings (heartbeats, timeouts, failure detection) are tuned to balance:
 * <ul>
 * <li><strong>Fast failure detection (~15 seconds):</strong> Detect and respond to node failures
 * quickly to maintain service availability</li>
 * <li><strong>Tolerance for GC pauses:</strong> Phi Accrual failure detector adapts to occasional
 * latency spikes from garbage collection</li>
 * <li><strong>Network partition handling:</strong> Split-brain protection prevents data
 * inconsistency when the network temporarily partitions</li>
 * </ul>
 *
 * <h2>Related Classes</h2>
 * <ul>
 * <li>{@link EurekaInstanceHelper} - Discovers cluster nodes from Eureka service registry;
 * used by both client discovery and runtime cluster management</li>
 * <li>{@link HazelcastClusterManager} - Manages runtime cluster connectivity; connects to
 * newly discovered members and handles cluster topology changes</li>
 * <li>{@link EurekaHazelcastDiscoveryStrategy} - Hazelcast SPI implementation that queries
 * Eureka for core node addresses during client connection</li>
 * </ul>
 *
 * @see <a href="https://docs.hazelcast.com/hazelcast/latest/">Hazelcast Documentation</a>
 */
@Conditional(CoreOrHazelcastBuildAgent.class)
@Lazy(value = false)
@Configuration
@EnableCaching
public class HazelcastConfiguration {

    private static final Logger log = LoggerFactory.getLogger(HazelcastConfiguration.class);

    private final ServerProperties serverProperties;

    private final Optional<Registration> registration;

    private final ApplicationContext applicationContext;

    private final Environment env;

    private final EurekaInstanceHelper eurekaInstanceHelper;

    @Value("${spring.jpa.properties.hibernate.cache.hazelcast.instance_name}")
    private String instanceName;

    @Value("${spring.hazelcast.interface:}")
    private String hazelcastInterface;

    @Value("${spring.hazelcast.port:5701}")
    private int hazelcastPort;

    @Value("${spring.hazelcast.localInstances:true}")
    private boolean hazelcastLocalInstances;

    /**
     * Creates the HazelcastConfiguration with required dependencies.
     *
     * <p>
     * <strong>Dependencies:</strong>
     * <ul>
     * <li>{@code applicationContext}: For Spring integration (managed context, classloader)</li>
     * <li>{@code serverProperties}: For HTTP server port (used in local instances mode)</li>
     * <li>{@code registration}: Eureka service registration for metadata publishing (optional
     * because single-node deployments may not use Eureka)</li>
     * <li>{@code eurekaInstanceHelper}: For service discovery (required for client mode)</li>
     * <li>{@code env}: For checking active Spring profiles</li>
     * </ul>
     *
     * <p>
     * <strong>Telemetry Disabled:</strong> Hazelcast's "phone home" feature sends usage
     * statistics to Hazelcast Inc. This is disabled for privacy and to avoid unexpected
     * network traffic in production environments.
     *
     * @param applicationContext   Spring application context for integration
     * @param serverProperties     server configuration including HTTP port
     * @param registration         Eureka registration for service discovery metadata (optional)
     * @param eurekaInstanceHelper helper for discovering other Hazelcast nodes
     * @param env                  Spring environment for profile checking
     */
    public HazelcastConfiguration(ApplicationContext applicationContext, ServerProperties serverProperties, Optional<Registration> registration,
            EurekaInstanceHelper eurekaInstanceHelper, Environment env) {
        this.applicationContext = applicationContext;
        this.serverProperties = serverProperties;
        this.registration = registration;
        this.eurekaInstanceHelper = eurekaInstanceHelper;
        this.env = env;

        // Disable Hazelcast telemetry
        // https://docs.hazelcast.com/hazelcast/5.5/phone-homes
        System.setProperty("hazelcast.phone.home.enabled", "false");
    }

    /**
     * Gracefully shuts down all Hazelcast instances when the Spring context is destroyed.
     *
     * <p>
     * <strong>Shutdown Order:</strong> Both cluster members and clients are shut down.
     * This method is called during application shutdown (triggered by {@code @PreDestroy}).
     *
     * <p>
     * <strong>Graceful Shutdown:</strong> Hazelcast's shutdown process:
     * <ol>
     * <li>Notifies other cluster members of departure</li>
     * <li>Migrates owned partitions to remaining members (for cluster members)</li>
     * <li>Closes network connections</li>
     * <li>Releases resources</li>
     * </ol>
     *
     * <p>
     * <strong>Why Both shutdownAll() Calls:</strong> Depending on the deployment mode,
     * this application may have created either a cluster member instance or a client
     * instance. Calling both shutdown methods ensures cleanup regardless of mode.
     */
    @PreDestroy
    public void destroy() {
        log.info("Shutting down Hazelcast");
        Hazelcast.shutdownAll();
        HazelcastClient.shutdownAll();
    }

    // ==================== Spring Cache Beans ====================

    /**
     * Creates the Spring {@link CacheManager} backed by Hazelcast.
     *
     * <p>
     * <strong>Rationale:</strong> Using Hazelcast as the cache backend (vs. local caches like Caffeine)
     * provides distributed caching across all Artemis nodes. When one node caches data, all other
     * nodes can access it without hitting the database. This is critical for:
     * <ul>
     * <li>User authentication data - avoids repeated database lookups</li>
     * <li>Course/exercise metadata - frequently accessed, rarely changed</li>
     * <li>Rate limiting state - must be consistent across all nodes</li>
     * </ul>
     *
     * <p>
     * The {@code @Qualifier} ensures we get the correct HazelcastInstance when multiple beans exist.
     *
     * @param hazelcastInstance the Hazelcast instance to back the cache
     * @return the Hazelcast-backed CacheManager for Spring's caching abstraction
     */
    @Bean
    public CacheManager cacheManager(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance) {
        log.debug("Starting HazelcastCacheManager");
        return new HazelcastCacheManager(hazelcastInstance);
    }

    /**
     * Creates a cache key generator that includes build information in cache keys.
     *
     * <p>
     * <strong>Rationale:</strong> Including git commit hash and build version in cache keys ensures
     * that cache entries from different application versions don't collide. This is important during
     * rolling deployments where nodes running different versions coexist temporarily. Without this,
     * serialization incompatibilities between versions could cause errors.
     *
     * @param gitProperties   git commit information (commit hash, branch)
     * @param buildProperties build metadata (version, timestamp)
     * @return a key generator that prefixes cache keys with version information
     */
    @Bean
    public KeyGenerator keyGenerator(GitProperties gitProperties, BuildProperties buildProperties) {
        return new PrefixedKeyGenerator(gitProperties, buildProperties);
    }

    // ==================== Hazelcast Instance Creation ====================

    /**
     * Creates the appropriate HazelcastInstance based on active Spring profiles.
     *
     * <p>
     * This is the main entry point for Hazelcast initialization. The method routes to
     * one of three configurations based on the deployment context:
     *
     * <h3>Decision Logic</h3>
     *
     * <pre>
     * if (test profile active && not test-independent) -> isolated test instance
     * else if (buildagent profile && not core profile && not test-buildagent) -> Hazelcast client
     * else -> cluster member
     * </pre>
     *
     * <h3>Configuration Paths</h3>
     * <ul>
     * <li><strong>Test Instance:</strong> Fully isolated, random port/cluster name, no discovery.
     * Used by integration tests to prevent cross-test interference.</li>
     *
     * <li><strong>Hazelcast Client:</strong> Connects to existing core cluster without joining
     * as a member. Used by standalone build agents for cluster isolation.</li>
     *
     * <li><strong>Cluster Member:</strong> Full cluster participation with data ownership,
     * backups, and consensus. Used by core nodes and build agents running alongside core.</li>
     * </ul>
     *
     * <p>
     * <strong>Bean Naming:</strong> The explicit bean name "hazelcastInstance" is required
     * because other components (especially JCache/Hibernate integration) look up the
     * HazelcastInstance by this specific name.
     *
     * @param jHipsterProperties the JHipster properties containing cache configuration
     *                               (TTL, backup count, etc.)
     * @return the configured HazelcastInstance appropriate for the deployment context
     */
    @Bean(name = "hazelcastInstance")
    public HazelcastInstance hazelcastInstance(JHipsterProperties jHipsterProperties) {
        if (isTestEnvironment()) {
            return createTestHazelcastInstance(jHipsterProperties);
        }
        if (shouldRunAsHazelcastClient()) {
            log.info("Build agent connecting to core cluster as Hazelcast client");
            return createHazelcastClient();
        }
        return createClusterMemberInstance(jHipsterProperties);
    }

    /**
     * Checks if running in a test environment that requires isolated Hazelcast.
     *
     * <p>
     * <strong>Test Profile:</strong> The standard Spring {@code test} profile indicates
     * test execution context.
     *
     * <p>
     * <strong>Test-Independent Exception:</strong> The {@code test-independent} profile is
     * used for tests that intentionally test multi-node behavior. These tests need real
     * cluster formation, not isolated instances.
     *
     * @return true if this is a test environment requiring isolated Hazelcast
     */
    private boolean isTestEnvironment() {
        return env.acceptsProfiles(Profiles.of(SPRING_PROFILE_TEST)) && !env.acceptsProfiles(Profiles.of(PROFILE_TEST_INDEPENDENT));
    }

    /**
     * Determines if this instance should run as a Hazelcast client (not a cluster member).
     *
     * <p>
     * <strong>Client Mode Criteria:</strong>
     * <ul>
     * <li>Has {@code buildagent} profile (is a build agent)</li>
     * <li>Does NOT have {@code core} profile (not running alongside core)</li>
     * <li>Does NOT have {@code test-buildagent} profile (not in test mode)</li>
     * </ul>
     *
     * <p>
     * <strong>Why These Criteria:</strong>
     * <ul>
     * <li>Build agents without core are standalone services that should connect as clients</li>
     * <li>Build agents WITH core (same JVM) should join as cluster members for efficiency</li>
     * <li>Test build agents use the test instance configuration for isolation</li>
     * </ul>
     *
     * @return true if this instance should connect as a Hazelcast client
     */
    private boolean shouldRunAsHazelcastClient() {
        Collection<String> activeProfiles = Arrays.asList(env.getActiveProfiles());
        return activeProfiles.contains(PROFILE_BUILDAGENT) && !activeProfiles.contains(PROFILE_CORE) && !activeProfiles.contains(PROFILE_TEST_BUILDAGENT);
    }

    // ==================== Test Instance Configuration ====================

    /**
     * Creates an isolated Hazelcast instance for test environments.
     *
     * <p>
     * <strong>Isolation Strategy:</strong> Each test execution gets a completely independent
     * Hazelcast instance that cannot communicate with any other instance. This is achieved through:
     * <ol>
     * <li><strong>Random cluster name:</strong> UUID-based cluster name ensures no two instances
     * can ever join the same cluster, even if they happen to find each other</li>
     * <li><strong>Dynamic port allocation:</strong> Binds to a random available port, avoiding
     * conflicts when multiple test JVMs run on the same machine</li>
     * <li><strong>Disabled discovery:</strong> All discovery mechanisms (multicast, TCP/IP, AWS,
     * Kubernetes, Eureka) are disabled to prevent any cluster formation attempts</li>
     * </ol>
     *
     * <p>
     * <strong>Why isolation matters:</strong> Integration tests often manipulate cache state
     * (clearing caches, inserting test data). Without isolation, parallel test execution causes:
     * <ul>
     * <li>Test A clears a cache that Test B populated, causing Test B to fail</li>
     * <li>Test A reads stale data from Test B's previous run</li>
     * <li>Race conditions in cache access leading to flaky tests</li>
     * </ul>
     *
     * @param jHipsterProperties configuration for cache maps (TTL, backup count)
     * @return a fully isolated HazelcastInstance for testing
     */
    private HazelcastInstance createTestHazelcastInstance(JHipsterProperties jHipsterProperties) {
        log.debug("Creating isolated Hazelcast instance for testing");

        Config config = new Config();
        config.setInstanceName(instanceName);
        config.setClusterName("test-cluster-" + UUID.randomUUID());

        configureCacheMaps(config, jHipsterProperties);
        config.getSerializationConfig().addSerializerConfig(createPathSerializerConfig());

        configureIsolatedNetworking(config);

        return Hazelcast.newHazelcastInstance(config);
    }

    /**
     * Configures networking to completely prevent any cluster formation in test environments.
     *
     * <p>
     * <strong>Disabled Discovery Mechanisms:</strong>
     * <ul>
     * <li><strong>Multicast:</strong> UDP-based discovery that could find other test instances
     * on the same network segment</li>
     * <li><strong>TCP/IP:</strong> Static member list - disabled because we don't want any
     * static configuration in tests</li>
     * <li><strong>AWS/Kubernetes/Eureka:</strong> Cloud discovery mechanisms irrelevant for
     * local test execution but explicitly disabled for safety</li>
     * </ul>
     *
     * <p>
     * <strong>Port Configuration:</strong> Uses {@link #findAvailablePort()} to bind to a
     * random available port. Port auto-increment is disabled because we want exactly one port -
     * if the chosen port fails, something is wrong and we should fail fast rather than silently
     * binding to a different port.
     *
     * <p>
     * <strong>Localhost binding:</strong> Forces binding to 127.0.0.1 to ensure the instance
     * is only accessible locally, adding another layer of isolation.
     *
     * @param config the Hazelcast configuration to modify
     */
    private void configureIsolatedNetworking(Config config) {
        var joinConfig = config.getNetworkConfig().getJoin();
        joinConfig.getMulticastConfig().setEnabled(false);
        joinConfig.getTcpIpConfig().setEnabled(false);
        joinConfig.getAwsConfig().setEnabled(false);
        joinConfig.getKubernetesConfig().setEnabled(false);
        joinConfig.getEurekaConfig().setEnabled(false);

        config.getNetworkConfig().setPort(findAvailablePort());
        config.getNetworkConfig().setPortAutoIncrement(false);
        config.setProperty("hazelcast.local.localAddress", "127.0.0.1");
    }

    // ==================== Cluster Member Configuration ====================

    /**
     * Creates a Hazelcast cluster member instance for production use.
     *
     * <p>
     * <strong>Configuration Steps:</strong> This method orchestrates the complete cluster member
     * setup through a series of focused configuration methods:
     * <ol>
     * <li>Check for existing instance (prevents duplicate initialization)</li>
     * <li>Configure basic networking (TCP/IP discovery)</li>
     * <li>Set member attributes (for identification in management tools)</li>
     * <li>Enable Spring integration (for {@code @Async} distributed tasks)</li>
     * <li>Configure network binding and service registry metadata</li>
     * <li>Set up cache maps with eviction policies</li>
     * <li>Enable split-brain protection</li>
     * <li>Tune cluster stability settings (heartbeats, timeouts)</li>
     * <li>Configure LocalCI queue if applicable</li>
     * <li>Set lite member mode for build agents joining as members</li>
     * </ol>
     *
     * <p>
     * <strong>Instance Reuse:</strong> The check for existing instance by name prevents
     * accidental double-initialization, which could occur if Spring recreates this bean
     * during context refresh.
     *
     * @param jHipsterProperties configuration containing cache TTL, backup count settings
     * @return the configured HazelcastInstance ready for cluster participation
     */
    private HazelcastInstance createClusterMemberInstance(JHipsterProperties jHipsterProperties) {
        log.debug("Configuring Hazelcast cluster member");

        HazelcastInstance existingInstance = Hazelcast.getHazelcastInstanceByName(instanceName);
        if (existingInstance != null) {
            log.debug("Hazelcast already initialized");
            return existingInstance;
        }

        Config config = new Config();
        config.setInstanceName(instanceName);

        configureBasicNetworking(config);
        configureMemberAttributes(config);
        configureSpringIntegration(config);
        config.getSerializationConfig().addSerializerConfig(createPathSerializerConfig());

        configureNetworkBindingAndDiscovery(config);
        configureCacheMaps(config, jHipsterProperties);
        configureSplitBrainProtection(config);
        configureClusterStabilitySettings(config);
        configureLocalCIQueueIfNeeded(config, jHipsterProperties);
        configureLiteMemberIfBuildAgent(config);

        return Hazelcast.newHazelcastInstance(config);
    }

    /**
     * Configures basic networking: disables multicast and auto-detection, enables TCP/IP.
     *
     * <p>
     * <strong>Why disable multicast:</strong>
     * <ul>
     * <li>Multicast is often blocked in cloud environments (AWS, Azure, GCP VPCs)</li>
     * <li>Multicast can accidentally discover unrelated Hazelcast instances on the same network</li>
     * <li>Multicast discovery is non-deterministic and can cause split-brain during network issues</li>
     * </ul>
     *
     * <p>
     * <strong>Why disable auto-detection:</strong> Hazelcast's auto-detection tries multiple
     * discovery mechanisms which can cause confusion and unexpected behavior. Explicit TCP/IP
     * configuration with Eureka-based discovery provides predictable behavior.
     *
     * <p>
     * <strong>Why enable TCP/IP:</strong> TCP/IP join provides reliable, point-to-point
     * communication. Combined with Eureka service discovery (configured in
     * {@link HazelcastClusterManager}), nodes can find each other dynamically.
     *
     * @param config the Hazelcast configuration to modify
     */
    private void configureBasicNetworking(Config config) {
        var joinConfig = config.getNetworkConfig().getJoin();
        joinConfig.getMulticastConfig().setEnabled(false);
        joinConfig.getAutoDetectionConfig().setEnabled(false);
        joinConfig.getTcpIpConfig().setEnabled(true);
    }

    /**
     * Configures member attributes for identification in the cluster.
     *
     * <p>
     * <strong>Purpose:</strong> Member attributes provide human-readable identification for
     * cluster members in:
     * <ul>
     * <li>Hazelcast Management Center dashboards</li>
     * <li>Log messages when members join/leave</li>
     * <li>Debugging cluster issues (which node owns which partition)</li>
     * </ul>
     *
     * <p>
     * <strong>Attribute Selection:</strong> Uses the build agent display name if configured
     * (for build agents), otherwise falls back to the Eureka instance ID. This ensures
     * meaningful names like "build-agent-1" instead of UUIDs.
     *
     * @param config the Hazelcast configuration to modify
     */
    private void configureMemberAttributes(Config config) {
        String buildAgentDisplayName = env.getProperty("artemis.continuous-integration.build-agent.display-name", "");
        String instanceId = env.getProperty("eureka.instance.instanceId", "");
        String displayName = !buildAgentDisplayName.isBlank() && !buildAgentDisplayName.equals("Unnamed Artemis Node") ? buildAgentDisplayName : instanceId;

        if (!displayName.isBlank()) {
            MemberAttributeConfig memberAttributeConfig = config.getMemberAttributeConfig();
            log.info("Using instanceId '{}' for Hazelcast member attributes", displayName);
            memberAttributeConfig.setAttribute("instanceId", displayName);
        }
    }

    /**
     * Configures Spring integration for distributed operations.
     *
     * <p>
     * <strong>SpringManagedContext:</strong> Enables Spring dependency injection in Hazelcast-managed
     * objects. This is required for:
     * <ul>
     * <li>Distributed executor tasks that need Spring beans</li>
     * <li>Entry processors that require service dependencies</li>
     * <li>Custom serializers that use Spring-managed components</li>
     * </ul>
     *
     * <p>
     * <strong>ClassLoader:</strong> Using Spring's application context classloader ensures
     * Hazelcast can deserialize objects using the same classloader that loaded the application.
     * This prevents {@code ClassNotFoundException} in distributed operations.
     *
     * @param config the Hazelcast configuration to modify
     */
    private void configureSpringIntegration(Config config) {
        config.setManagedContext(new SpringManagedContext(applicationContext));
        config.setClassLoader(applicationContext.getClassLoader());
    }

    /**
     * Configures network binding and registers service discovery metadata.
     *
     * <p>
     * <strong>Without Service Registry:</strong> If no Eureka registration is available,
     * Hazelcast binds to localhost only. This is a safe fallback that prevents accidental
     * network exposure but limits functionality to single-node operation.
     *
     * <p>
     * <strong>With Service Registry:</strong> Configures the network interface and port,
     * then registers Hazelcast connection metadata (host, port, member type) in Eureka.
     * Other nodes query this metadata via {@link EurekaInstanceHelper#discoverCoreNodeAddresses()}
     * to find cluster members.
     *
     * <p>
     * <strong>Member Type Metadata:</strong> Setting {@code hazelcast.member-type=member} distinguishes
     * core nodes from Hazelcast clients. The {@link EurekaHazelcastDiscoveryStrategy} uses this
     * to filter out client instances when discovering cluster members.
     *
     * @param config the Hazelcast configuration to modify
     */
    private void configureNetworkBindingAndDiscovery(Config config) {
        if (registration.isEmpty()) {
            log.info("No discovery service is set up, Hazelcast cannot create a multi-node cluster.");
            hazelcastBindOnlyOnInterface("127.0.0.1", config);
            return;
        }

        String serviceId = registration.get().getServiceId();
        log.info("Configuring Hazelcast clustering for instanceId: {}", serviceId);

        configureNetworkInterface(config);
        configurePortAndMetadata(config);
        // Note: Hazelcast address metadata is set in configurePortAndMetadata via eurekaInstanceHelper.registerHazelcastAddress()
    }

    /**
     * Configures which network interface Hazelcast binds to.
     *
     * <p>
     * <strong>Explicit Interface Binding:</strong> By default, Hazelcast may bind to any available
     * interface, which causes problems in multi-homed environments (e.g., Docker containers with
     * both bridge and host networks). Explicit binding ensures:
     * <ul>
     * <li>Predictable network behavior</li>
     * <li>Correct public address advertisement to other members</li>
     * <li>Firewall rules work as expected</li>
     * </ul>
     *
     * <p>
     * <strong>IPv6 Support:</strong> When a specific interface is configured, we disable the
     * "prefer IPv4" setting to allow IPv6 addresses. This is important for modern deployments
     * that may use IPv6 internally.
     *
     * <p>
     * <strong>Default to Localhost:</strong> When no interface is explicitly configured, binding
     * to 127.0.0.1 is the safest default - it prevents unintended network exposure.
     *
     * @param config the Hazelcast configuration to modify
     */
    private void configureNetworkInterface(Config config) {
        if (hazelcastInterface != null && !hazelcastInterface.isEmpty()) {
            System.setProperty("hazelcast.prefer.ipv4.stack", "false");
            hazelcastBindOnlyOnInterface(hazelcastInterface, config);
        }
        else {
            log.info("Binding Hazelcast to default interface");
            hazelcastBindOnlyOnInterface("127.0.0.1", config);
        }
    }

    /**
     * Configures port and registers metadata for service discovery.
     *
     * <p>
     * <strong>Local Instances Mode ({@code spring.hazelcast.localInstances=true}):</strong>
     * For development environments running multiple Artemis instances on a single machine,
     * the Hazelcast port is derived from the HTTP server port (e.g., server port 8080 -> Hazelcast
     * port 8080 + 5701 = 13781). This ensures each instance gets a unique port.
     *
     * <p>
     * <strong>Production Mode ({@code spring.hazelcast.localInstances=false}):</strong>
     * Uses the standard Hazelcast port (default 5701) and sets cluster name to "prod".
     * All production nodes use the same port because they run on different machines.
     *
     * <p>
     * <strong>Metadata Registration:</strong> The Hazelcast host and port are registered as
     * Eureka metadata via {@link EurekaInstanceHelper#registerHazelcastAddress}, which also triggers
     * an immediate re-registration to propagate the metadata. This ensures build agents can
     * discover core nodes without waiting for the next heartbeat.
     *
     * @param config the Hazelcast configuration to modify
     */
    private void configurePortAndMetadata(Config config) {
        String hazelcastMetadataHost = registration.get().getHost();
        int effectivePort;

        if (hazelcastLocalInstances) {
            log.info("Running with localInstances setting, Hazelcast cluster will only work with localhost instances");
            effectivePort = serverProperties.getPort() + hazelcastPort;
            config.getNetworkConfig().setPort(effectivePort);
        }
        else {
            config.setClusterName("prod");
            config.setInstanceName(instanceName);
            effectivePort = hazelcastPort;
            config.getNetworkConfig().setPort(effectivePort);
        }

        // Register Hazelcast address and trigger immediate Eureka re-registration
        // This ensures build agents can discover this core node immediately
        eurekaInstanceHelper.registerHazelcastAddress(hazelcastMetadataHost, effectivePort);
    }

    /**
     * Binds Hazelcast strictly to a single network interface.
     *
     * <p>
     * <strong>Why Strict Binding:</strong> By default, Hazelcast's socket binding behavior
     * can cause it to listen on all interfaces (0.0.0.0), which:
     * <ul>
     * <li>Exposes Hazelcast to unintended networks</li>
     * <li>Causes confusion when multiple network interfaces exist</li>
     * <li>May violate security policies requiring explicit interface binding</li>
     * </ul>
     *
     * <p>
     * <strong>Properties Explained:</strong>
     * <ul>
     * <li>{@code hazelcast.local.localAddress}: The address this member uses for intra-cluster
     * communication</li>
     * <li>{@code hazelcast.local.publicAddress}: The address advertised to other members</li>
     * <li>{@code hazelcast.socket.bind.any=false}: Prevents binding to 0.0.0.0</li>
     * <li>{@code hazelcast.socket.server.bind.any=false}: Prevents server sockets from binding
     * to all interfaces</li>
     * <li>{@code hazelcast.socket.client.bind.any=false}: Prevents client sockets from binding
     * to all interfaces</li>
     * </ul>
     *
     * @param networkInterface the specific interface to bind to (e.g., "192.168.1.100" or "eth0")
     * @param config           the Hazelcast configuration to modify
     */
    private void hazelcastBindOnlyOnInterface(String networkInterface, Config config) {
        log.debug("Binding Hazelcast to interface {}", networkInterface);
        System.setProperty("hazelcast.local.localAddress", networkInterface);
        System.setProperty("hazelcast.local.publicAddress", networkInterface);
        config.getNetworkConfig().getInterfaces().setEnabled(true).setInterfaces(Collections.singleton(networkInterface));

        config.setProperty("hazelcast.socket.bind.any", "false");
        config.setProperty("hazelcast.socket.server.bind.any", "false");
        config.setProperty("hazelcast.socket.client.bind.any", "false");
    }

    /**
     * Configures split-brain protection to handle network partitions.
     *
     * <p>
     * <strong>What is Split-Brain:</strong> When a network partition occurs, cluster members
     * can't communicate and may form separate sub-clusters. Each sub-cluster thinks it's the
     * "real" cluster and continues operating independently. When the partition heals, data
     * inconsistencies must be resolved.
     *
     * <p>
     * <strong>Protection Mechanism:</strong> Split-brain protection defines a minimum cluster
     * size (quorum) required for operations. With {@code minimumClusterSize=2}, a single
     * isolated node cannot perform cluster operations, preventing inconsistent writes.
     *
     * <p>
     * <strong>Merge Policy:</strong> The merge delay settings (30 seconds for first run,
     * 30 seconds between subsequent runs) control how quickly Hazelcast attempts to merge
     * sub-clusters after a partition heals. The delay prevents thrashing during unstable
     * network conditions.
     *
     * <p>
     * <strong>Trade-off:</strong> This configuration prioritizes consistency over availability.
     * A single-node partition becomes read-only, which is acceptable for Artemis where data
     * consistency is more important than availability during network issues.
     *
     * @param config the Hazelcast configuration to modify
     */
    private void configureSplitBrainProtection(Config config) {
        var splitBrainConfig = new SplitBrainProtectionConfig();
        splitBrainConfig.setName("artemis-split-brain-protection");
        splitBrainConfig.setEnabled(true);
        splitBrainConfig.setMinimumClusterSize(2);

        config.setSplitBrainProtectionConfigs(new ConcurrentHashMap<>());
        config.addSplitBrainProtectionConfig(splitBrainConfig);
        config.setProperty(ClusterProperty.MERGE_FIRST_RUN_DELAY_SECONDS.getName(), "30");
        config.setProperty(ClusterProperty.MERGE_NEXT_RUN_DELAY_SECONDS.getName(), "30");
    }

    /**
     * Configures cluster stability settings: failure detection, heartbeats, and timeouts.
     *
     * <p>
     * <strong>Design Goals:</strong>
     * <ul>
     * <li>Detect node failures within ~15 seconds</li>
     * <li>Tolerate occasional GC pauses without false positives</li>
     * <li>Fail fast on unresponsive operations to prevent request pile-up</li>
     * <li>Provide visibility into slow operations for debugging</li>
     * </ul>
     *
     * <h3>Phi Accrual Failure Detector</h3>
     * <p>
     * Unlike a simple timeout-based detector, Phi Accrual adapts to the historical heartbeat
     * pattern. It calculates a "suspicion level" (phi) based on how late a heartbeat is
     * relative to the expected arrival distribution. This provides:
     * <ul>
     * <li>Tolerance for network jitter and occasional delays</li>
     * <li>Fast detection of actual failures</li>
     * <li>Reduced false positives during GC pauses</li>
     * </ul>
     * <p>
     * Settings: threshold=8 (moderate sensitivity), sample size=100 (statistical accuracy),
     * min std dev=100ms (baseline variance tolerance).
     *
     * <h3>Heartbeat Configuration</h3>
     * <p>
     * Heartbeats every 5 seconds with a 15-second timeout balances:
     * <ul>
     * <li>Fast failure detection (3 missed heartbeats = suspected failure)</li>
     * <li>Low network overhead (heartbeats are small but add up in large clusters)</li>
     * <li>Tolerance for temporary network blips</li>
     * </ul>
     *
     * <h3>Operation Timeouts</h3>
     * <ul>
     * <li><strong>Call timeout (15s):</strong> Maximum time to wait for a distributed operation
     * response. Prevents indefinite blocking on unresponsive members.</li>
     * <li><strong>Backup timeout (5s):</strong> Time to wait for backup acknowledgment. Shorter
     * because backups should be fast.</li>
     * </ul>
     *
     * <h3>Invocation Retry</h3>
     * <p>
     * Limited to 5 retries with 1-second pause. Prevents infinite retry loops that could
     * mask underlying issues while still handling transient failures.
     *
     * <h3>Slow Operation Detection</h3>
     * <p>
     * Operations taking longer than 5 seconds are logged for debugging. Logs are retained
     * for 5 minutes, providing visibility into performance issues without excessive log growth.
     *
     * @param config the Hazelcast configuration to modify
     */
    private void configureClusterStabilitySettings(Config config) {
        // Phi Accrual failure detector - adaptive based on heartbeat history
        config.setProperty("hazelcast.heartbeat.failuredetector.type", "phi-accrual");
        config.setProperty("hazelcast.heartbeat.phiaccrual.failuredetector.threshold", "8");
        config.setProperty("hazelcast.heartbeat.phiaccrual.failuredetector.sample.size", "100");
        config.setProperty("hazelcast.heartbeat.phiaccrual.failuredetector.min.std.dev.millis", "100");

        // Heartbeat configuration - detect failures within ~15 seconds
        config.setProperty(ClusterProperty.HEARTBEAT_INTERVAL_SECONDS.getName(), "5");
        config.setProperty(ClusterProperty.MAX_NO_HEARTBEAT_SECONDS.getName(), "15");

        // Operation timeouts - fail fast on unresponsive members
        config.setProperty(ClusterProperty.OPERATION_CALL_TIMEOUT_MILLIS.getName(), "15000");
        config.setProperty(ClusterProperty.OPERATION_BACKUP_TIMEOUT_MILLIS.getName(), "5000");

        // Invocation retry - limited retries instead of indefinite
        config.setProperty(ClusterProperty.INVOCATION_MAX_RETRY_COUNT.getName(), "5");
        config.setProperty(ClusterProperty.INVOCATION_RETRY_PAUSE.getName(), "1000");

        // Slow operation detection
        config.setProperty(ClusterProperty.SLOW_OPERATION_DETECTOR_THRESHOLD_MILLIS.getName(), "5000");
        config.setProperty(ClusterProperty.SLOW_OPERATION_DETECTOR_LOG_RETENTION_SECONDS.getName(), "300");

        // Connection timeout
        config.setProperty(ClusterProperty.SOCKET_CONNECT_TIMEOUT_SECONDS.getName(), "5");

        // Client (build agent) heartbeat detection - detect unresponsive clients faster
        // This is the SERVER-SIDE timeout for detecting unresponsive Hazelcast clients.
        // When a build agent is frozen (e.g., kill -STOP), the core node uses this timeout
        // to detect the client is gone. Default is 300 seconds (5 minutes), which is too slow.
        // Set to 30 seconds to provide 2x buffer over client-side timeout (15s) for network
        // jitter tolerance while still detecting frozen build agents within a reasonable timeframe.
        // Note: The client-side properties (hazelcast.client.heartbeat.*) configured in
        // configureClientHeartbeat() control how clients detect server disconnection, not the reverse.
        config.setProperty(ClusterProperty.CLIENT_HEARTBEAT_TIMEOUT_SECONDS.getName(), "30");
    }

    /**
     * Configures the LocalCI build job queue if LocalCI or BuildAgent profiles are active.
     *
     * <p>
     * <strong>Purpose:</strong> The build job queue is a distributed priority queue that holds
     * pending CI/CD build jobs. Build agents pull jobs from this queue and execute them.
     *
     * <p>
     * <strong>Priority Queue:</strong> Uses {@link LocalCIPriorityQueueComparator} to order
     * jobs by priority. This ensures high-priority builds (e.g., exam submissions) are
     * processed before lower-priority ones (e.g., practice submissions).
     *
     * <p>
     * <strong>Backup Count:</strong> Configured from JHipster properties to ensure queue
     * durability. If the primary owner fails, a backup takes over without losing jobs.
     *
     * <p>
     * <strong>Profile Check:</strong> Only configured when LocalCI or BuildAgent profiles
     * are active, avoiding unnecessary resource allocation in deployments that don't use
     * local CI (e.g., external CI systems like Jenkins).
     *
     * @param config             the Hazelcast configuration to modify
     * @param jHipsterProperties configuration for backup count
     */
    private void configureLocalCIQueueIfNeeded(Config config, JHipsterProperties jHipsterProperties) {
        Collection<String> activeProfiles = Arrays.asList(env.getActiveProfiles());
        if (activeProfiles.contains(PROFILE_LOCALCI) || activeProfiles.contains(PROFILE_BUILDAGENT)) {
            log.debug("Configuring Build Job Queue for Local CI");
            QueueConfig queueConfig = new QueueConfig("buildJobQueue");
            queueConfig.setBackupCount(jHipsterProperties.getCache().getHazelcast().getBackupCount());
            queueConfig.setPriorityComparatorClassName(LocalCIPriorityQueueComparator.class.getName());
            config.addQueueConfig(queueConfig);
        }
    }

    /**
     * Configures build agents as lite members when they join as cluster members.
     *
     * <p>
     * <strong>What are Lite Members:</strong> Lite members participate in cluster membership
     * and can access distributed data structures, but they don't own any data partitions.
     * All data is stored on "data members" (core nodes).
     *
     * <p>
     * <strong>Why Lite Members for Build Agents:</strong>
     * <ul>
     * <li>Build agents are ephemeral - they may be scaled up/down frequently. Lite member
     * status means no data migration occurs when they join/leave.</li>
     * <li>Build agents don't need to store data - they only consume jobs from queues and
     * write results. Data storage should be on stable core nodes.</li>
     * <li>Reduces memory pressure on build agents - they don't cache partitions of the
     * distributed maps.</li>
     * </ul>
     *
     * <p>
     * <strong>Note:</strong> This applies only when build agents join as cluster members
     * (legacy mode). The preferred approach is client mode (see {@link #createHazelcastClient()}).
     *
     * @param config the Hazelcast configuration to modify
     */
    private void configureLiteMemberIfBuildAgent(Config config) {
        Collection<String> activeProfiles = Arrays.asList(env.getActiveProfiles());
        if (!activeProfiles.contains(PROFILE_TEST_BUILDAGENT) && !activeProfiles.contains(PROFILE_CORE) && activeProfiles.contains(PROFILE_BUILDAGENT)) {
            log.info("Joining cluster as lite member");
            config.setLiteMember(true);
        }
    }

    // ==================== Client Configuration ====================

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
    private HazelcastInstance createHazelcastClient() {
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
        clientConfig.getSerializationConfig().addSerializerConfig(createPathSerializerConfig());

        log.info("Creating Hazelcast client with Eureka-based dynamic discovery");

        return HazelcastClient.newHazelcastClient(clientConfig);
    }

    /**
     * Configures client identity and cluster name.
     *
     * <p>
     * <strong>Instance Name:</strong> Uses the build agent's short name for identification
     * in logs and management tools. Falls back to instance name + "-client" suffix if not
     * configured.
     *
     * <p>
     * <strong>Cluster Name:</strong> In production mode ({@code spring.hazelcast.localInstances=false}),
     * the cluster name is set to "prod" to match the core cluster configuration. Clients must
     * use the same cluster name as the cluster they're connecting to.
     *
     * @param clientConfig the client configuration to modify
     */
    private void configureClientIdentity(ClientConfig clientConfig) {
        String buildAgentShortName = env.getProperty("artemis.continuous-integration.build-agent.short-name", instanceName + "-client");
        clientConfig.setInstanceName(buildAgentShortName);

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

    // ==================== Cache Map Configuration ====================

    /**
     * Configures all cache maps with appropriate eviction and backup policies.
     *
     * <p>
     * <strong>Map Configuration Strategy:</strong> Different types of cached data have
     * different access patterns and durability requirements. This method registers
     * configurations for:
     * <ul>
     * <li><strong>default:</strong> Fallback for any map not explicitly configured</li>
     * <li><strong>files:</strong> Cached file content with TTL for staleness prevention</li>
     * <li><strong>domain entities:</strong> Hibernate second-level cache entries</li>
     * <li><strong>rate-limit-buckets:</strong> API rate limiting state</li>
     * <li><strong>atlas-session-pending-operations:</strong> Long-lived session state</li>
     * </ul>
     *
     * <p>
     * <strong>Wildcard Patterns:</strong> Map names can use wildcards (e.g.,
     * {@code de.tum.cit.aet.artemis.*.domain.*}) to apply configuration to all matching maps.
     * This is used for Hibernate entity caches which are named after their class names.
     *
     * @param config             the Hazelcast configuration to modify
     * @param jHipsterProperties configuration for TTL and backup count
     */
    private void configureCacheMaps(Config config, JHipsterProperties jHipsterProperties) {
        config.getMapConfigs().put("default", createDefaultMapConfig(jHipsterProperties));
        config.getMapConfigs().put("files", createFilesMapConfig(jHipsterProperties));
        config.getMapConfigs().put("de.tum.cit.aet.artemis.*.domain.*", createDomainMapConfig(jHipsterProperties));
        config.getMapConfigs().put("rate-limit-buckets", createRateLimitBucketsMapConfig(jHipsterProperties));
        config.getMapConfigs().put("atlas-session-pending-operations", createAtlasSessionMapConfig(jHipsterProperties));
    }

    /**
     * Creates the default map configuration used for any map not explicitly configured.
     *
     * <p>
     * <strong>Backup Count:</strong> Configurable via JHipster properties. Backups ensure
     * data survives single-node failures. Typical values:
     * <ul>
     * <li>0: No backups (fastest, but data loss on node failure)</li>
     * <li>1: One synchronous backup (recommended for most caches)</li>
     * <li>2+: Multiple backups (for critical data)</li>
     * </ul>
     *
     * <p>
     * <strong>Eviction Policy (LRU):</strong> Least Recently Used eviction removes entries
     * that haven't been accessed recently when memory limits are reached. This is appropriate
     * for caches where recent data is more valuable.
     *
     * <p>
     * <strong>Max Size Policy (PER_NODE):</strong> Memory limits are evaluated per cluster
     * member, not globally. This prevents a single node from being overwhelmed with data
     * while others have capacity.
     *
     * @param jHipsterProperties configuration for backup count
     * @return the default map configuration
     */
    private MapConfig createDefaultMapConfig(JHipsterProperties jHipsterProperties) {
        return new MapConfig().setBackupCount(jHipsterProperties.getCache().getHazelcast().getBackupCount())
                .setEvictionConfig(new EvictionConfig().setEvictionPolicy(EvictionPolicy.LRU).setMaxSizePolicy(MaxSizePolicy.PER_NODE));
    }

    /**
     * Creates configuration for the file content cache.
     *
     * <p>
     * <strong>Purpose:</strong> Caches file content (e.g., images, attachments) to avoid
     * repeated disk reads. Used by {@link FileService#getFileForPath}.
     *
     * <p>
     * <strong>TTL Configuration:</strong> Files are cached with a time-to-live to ensure
     * eventual consistency when files are updated on disk. Without TTL, cached versions
     * could become stale if files are modified outside of Artemis.
     *
     * <p>
     * <strong>LRU Eviction:</strong> Ensures frequently accessed files stay in cache while
     * rarely accessed files are evicted when memory is needed.
     *
     * @param jHipsterProperties configuration for backup count and TTL
     * @return the file cache map configuration
     */
    private MapConfig createFilesMapConfig(JHipsterProperties jHipsterProperties) {
        return new MapConfig().setBackupCount(jHipsterProperties.getCache().getHazelcast().getBackupCount())
                .setEvictionConfig(new EvictionConfig().setEvictionPolicy(EvictionPolicy.LRU).setMaxSizePolicy(MaxSizePolicy.PER_NODE))
                .setTimeToLiveSeconds(jHipsterProperties.getCache().getHazelcast().getTimeToLiveSeconds());
    }

    /**
     * Creates configuration for Hibernate second-level cache (domain entities).
     *
     * <p>
     * <strong>Purpose:</strong> Caches JPA/Hibernate entities to reduce database queries.
     * The wildcard pattern {@code de.tum.cit.aet.artemis.*.domain.*} matches all entity
     * classes in domain packages (e.g., {@code Course}, {@code Exercise}, {@code User}).
     *
     * <p>
     * <strong>TTL Only (No Eviction):</strong> Entity caches use TTL for cache invalidation
     * but don't configure explicit eviction. Hibernate manages cache entries based on its
     * own cache region strategy. The TTL ensures stale entities are eventually refreshed.
     *
     * <p>
     * <strong>No Backup Count:</strong> Inherits from Hazelcast defaults. Entity caches
     * can be reconstructed from the database, so durability is less critical than for
     * session or rate-limit data.
     *
     * @param jHipsterProperties configuration for TTL
     * @return the domain entity cache map configuration
     */
    private MapConfig createDomainMapConfig(JHipsterProperties jHipsterProperties) {
        return new MapConfig().setTimeToLiveSeconds(jHipsterProperties.getCache().getHazelcast().getTimeToLiveSeconds());
    }

    /**
     * Creates configuration for API rate limiting buckets.
     *
     * <p>
     * <strong>Purpose:</strong> Stores rate limit state (request counts, timestamps) for
     * API endpoints. This data must be shared across all nodes to enforce global rate limits.
     *
     * <p>
     * <strong>Backup Configuration:</strong> Rate limit state needs backups to survive
     * node failures. Without backups, a node failure could reset rate limits for users
     * whose state was on that node, potentially allowing rate limit bypass.
     *
     * <p>
     * <strong>TTL Configuration:</strong> Rate limit buckets expire based on the rate limit
     * window (e.g., requests per minute). The TTL ensures stale entries don't accumulate
     * indefinitely.
     *
     * <p>
     * <strong>No Eviction:</strong> Rate limit state should not be evicted under memory
     * pressure - losing this state could allow rate limit bypass. The TTL handles cleanup.
     *
     * @param jHipsterProperties configuration for backup count and TTL
     * @return the rate limit buckets map configuration
     */
    private MapConfig createRateLimitBucketsMapConfig(JHipsterProperties jHipsterProperties) {
        return new MapConfig().setBackupCount(jHipsterProperties.getCache().getHazelcast().getBackupCount())
                .setTimeToLiveSeconds(jHipsterProperties.getCache().getHazelcast().getTimeToLiveSeconds());
    }

    /**
     * Creates configuration for Atlas Agent session state cache.
     *
     * <p>
     * <strong>Purpose:</strong> Stores pending competency operations for AI-assisted learning
     * sessions. This enables stateful interactions where the AI suggests competency changes
     * that are later confirmed by the user.
     *
     * <p>
     * <strong>2-Hour TTL:</strong> Sessions are expected to complete within a single user
     * interaction session. The 2-hour TTL provides generous buffer for interrupted sessions
     * while ensuring abandoned sessions are eventually cleaned up.
     *
     * <p>
     * <strong>LRU Eviction:</strong> If memory pressure occurs, least recently used sessions
     * are evicted. This is acceptable because:
     * <ul>
     * <li>Active sessions have recent access and won't be evicted</li>
     * <li>Evicted sessions can be restarted (inconvenient but not data loss)</li>
     * </ul>
     *
     * <p>
     * <strong>Backup Configuration:</strong> Session state is backed up to survive node
     * failures. This prevents users from losing in-progress work if their session's primary
     * node fails.
     *
     * @param jHipsterProperties configuration for backup count
     * @return the Atlas session cache map configuration
     */
    private MapConfig createAtlasSessionMapConfig(JHipsterProperties jHipsterProperties) {
        return new MapConfig().setBackupCount(jHipsterProperties.getCache().getHazelcast().getBackupCount())
                .setEvictionConfig(new EvictionConfig().setEvictionPolicy(EvictionPolicy.LRU).setMaxSizePolicy(MaxSizePolicy.PER_NODE)).setTimeToLiveSeconds(2 * 60 * 60);
    }

    // ==================== Utilities ====================

    /**
     * Creates a serializer configuration for {@link Path} objects.
     *
     * <p>
     * <strong>Why Custom Serialization:</strong> Java's {@link Path} interface doesn't implement
     * {@link java.io.Serializable}, so Hazelcast cannot serialize it by default. Build jobs
     * use {@link Path} objects for file locations, which need to be stored in distributed
     * data structures.
     *
     * <p>
     * <strong>Implementation:</strong> The {@link HazelcastPathSerializer} converts Path
     * objects to/from their string representation, which is portable across nodes even
     * if they have different file systems.
     *
     * @return a serializer configuration for Path objects
     * @see HazelcastPathSerializer
     */
    private SerializerConfig createPathSerializerConfig() {
        SerializerConfig serializerConfig = new SerializerConfig();
        serializerConfig.setTypeClass(Path.class);
        serializerConfig.setImplementation(new HazelcastPathSerializer());
        return serializerConfig;
    }

    /**
     * Finds an available TCP port for Hazelcast to bind to.
     *
     * <p>
     * <strong>Usage:</strong> Used in test environments where multiple Hazelcast instances
     * may run on the same machine. Each instance needs a unique port.
     *
     * <p>
     * <strong>Implementation:</strong> Creates a server socket bound to port 0 (which the OS
     * assigns to any available port), retrieves the assigned port, then closes the socket.
     * The {@code setReuseAddress(true)} allows immediate reuse of the port after the socket
     * is closed.
     *
     * <p>
     * <strong>Note:</strong> There's a small race condition window between closing this socket
     * and Hazelcast binding to the port. In practice, this rarely causes issues in test
     * environments where port contention is low.
     *
     * @return an available TCP port number
     * @throws IllegalStateException if no port could be found (should never happen in practice)
     */
    private static int findAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
        catch (IOException e) {
            throw new IllegalStateException("Could not find an available TCP port for Hazelcast", e);
        }
    }
}
