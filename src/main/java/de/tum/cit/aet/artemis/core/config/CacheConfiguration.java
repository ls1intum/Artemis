package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_TEST_BUILDAGENT;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_TEST_INDEPENDENT;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Random;
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
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import com.hazelcast.config.Config;
import com.hazelcast.config.EvictionConfig;
import com.hazelcast.config.EvictionPolicy;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.MaxSizePolicy;
import com.hazelcast.config.NetworkConfig;
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
 * Configures and initializes the Hazelcast-based distributed caching system for Artemis instances,
 * including default cache maps, file caching, serialization, clustering behavior, and split-brain protection.
 *
 * <p>
 * Establishes a Hazelcast cluster that supports synchronization and scalability in both local development and production
 * deployments. It also enables shared job queues for local continuous integration (CI) setups and supports
 * role-based membership (e.g., lite members for build agents).
 *
 * <p>
 * <strong>Responsibilities:</strong>
 * <ul>
 * <li>Defines Hazelcast cache maps with specific eviction and backup policies for domain objects and files.</li>
 * <li>Manages Hazelcast instance creation based on Spring profiles and environment-specific properties.</li>
 * <li>Registers a custom serializer for {@link java.nio.file.Path} to enable file caching across nodes.</li>
 * <li>Supports isolation in test environments to avoid interference between test executions by randomizing cluster names and ports.</li>
 * <li>Provides cluster configuration options for discovery-based and local-only deployments, using either
 * service registration metadata or loopback interfaces.</li>
 * <li>Sets up split-brain protection and conditions for when clustering should be enabled or disabled.</li>
 * <li>Registers Hazelcast-aware Spring beans such as {@link org.springframework.cache.CacheManager}
 * and {@link org.springframework.cache.interceptor.KeyGenerator}.</li>
 * </ul>
 *
 * <p>
 * <strong>Separation of Concerns:</strong>
 * This class encapsulates all Hazelcast configuration aspects, including topology, caching behavior,
 * and serialization. It is distinct from {@link HazelcastConnection}, which is responsible for
 * dynamically connecting cluster nodes at runtime based on service discovery. By decoupling static
 * configuration from runtime coordination, the system ensures better modularity, testability, and maintainability.
 */
@Profile({ PROFILE_CORE, PROFILE_BUILDAGENT })
@Lazy(value = false)
@Configuration
@EnableCaching
public class CacheConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CacheConfiguration.class);

    private final Optional<GitProperties> gitProperties;

    private final Optional<BuildProperties> buildProperties;

    private final ServerProperties serverProperties;

    // the service registry, in our current deployment this is the jhipster registry which offers a Eureka Server under the hood
    private final Optional<Registration> registration;

    private final ApplicationContext applicationContext;

    private final Environment env;

    @Value("${spring.jpa.properties.hibernate.cache.hazelcast.instance_name}")
    private String instanceName;

    @Value("${spring.hazelcast.interface:}")    // if not specified, it will be an empty string
    private String hazelcastInterface;

    @Value("${spring.hazelcast.port:5701}")
    private int hazelcastPort;

    @Value("${spring.hazelcast.localInstances:true}")
    private boolean hazelcastLocalInstances;

    public CacheConfiguration(ApplicationContext applicationContext, Optional<GitProperties> gitProperties, Optional<BuildProperties> buildProperties,
            ServerProperties serverProperties, Optional<Registration> registration, Environment env) {
        this.applicationContext = applicationContext;
        this.gitProperties = gitProperties;
        this.buildProperties = buildProperties;
        this.serverProperties = serverProperties;
        this.registration = registration;
        this.env = env;

        // Do not send telemetry to Hazelcast.
        // https://docs.hazelcast.com/hazelcast/5.5/phone-homes
        System.setProperty("hazelcast.phone.home.enabled", "false");
    }

    @PreDestroy
    public void destroy() {
        log.info("Closing Cache Manager");
        Hazelcast.shutdownAll();
    }

    @Bean
    public CacheManager cacheManager(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance) {
        log.debug("Starting HazelcastCacheManager");
        return new HazelcastCacheManager(hazelcastInstance);
    }

    /**
     * Setup the hazelcast instance based on the given jHipster properties and the enabled spring profiles.
     * Note: It does not connect to other instances, this is done in {@link HazelcastConnection#connectToAllMembers()}.
     *
     * @param jHipsterProperties the jhipster properties
     * @return the created HazelcastInstance
     */
    @Bean(name = "hazelcastInstance")
    public HazelcastInstance hazelcastInstance(JHipsterProperties jHipsterProperties) {
        // ========================= TESTING ONLY =========================
        if (env.acceptsProfiles(Profiles.of(SPRING_PROFILE_TEST)) && !env.acceptsProfiles(Profiles.of(PROFILE_TEST_INDEPENDENT))) {
            // try to avoid that parallel test executions interfere with each other
            Config testConfig = new Config();
            testConfig.setInstanceName(instanceName);
            testConfig.setClusterName("test-cluster-" + UUID.randomUUID());

            testConfig.getMapConfigs().put("default", initializeDefaultMapConfig(jHipsterProperties));
            testConfig.getMapConfigs().put("files", initializeFilesMapConfig(jHipsterProperties));
            testConfig.getMapConfigs().put("de.tum.cit.aet.artemis.*.domain.*", initializeDomainMapConfig(jHipsterProperties));

            testConfig.getSerializationConfig().addSerializerConfig(createPathSerializerConfig());

            NetworkConfig networkConfig = testConfig.getNetworkConfig();
            // Set network configuration to prevent joining other nodes
            networkConfig.getJoin().getMulticastConfig().setEnabled(false);
            networkConfig.getJoin().getTcpIpConfig().setEnabled(false);
            networkConfig.getJoin().getAwsConfig().setEnabled(false);
            networkConfig.getJoin().getKubernetesConfig().setEnabled(false);
            networkConfig.getJoin().getEurekaConfig().setEnabled(false);

            // Ensure the instance is a local-only, lite member to prevent connections
            networkConfig.setPort(15701 + new Random().nextInt(1000)); // Randomize port to prevent conflicts
            networkConfig.setPortAutoIncrement(false);
            testConfig.setProperty("hazelcast.local.localAddress", "127.0.0.1");

            // testConfig.setLiteMember(true); // Run as a lite member to avoid forming a full cluster
            return Hazelcast.newHazelcastInstance(testConfig);
        }
        // ========================= TESTING ONLY =========================

        log.debug("Configuring Hazelcast");
        HazelcastInstance hazelCastInstance = Hazelcast.getHazelcastInstanceByName(instanceName);
        if (hazelCastInstance != null) {
            log.debug("Hazelcast already initialized");
            return hazelCastInstance;
        }
        Config config = new Config();
        config.setInstanceName(instanceName);
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getAutoDetectionConfig().setEnabled(false);

        // Always enable TcpIp config: There has to be at least one join-config and we can not use multicast as this creates unwanted clusters
        // If registration == null -> this will never connect to any instance as no other ip addresses are added
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(true);

        // Allows using @SpringAware and therefore Spring Services in distributed tasks
        config.setManagedContext(new SpringManagedContext(applicationContext));
        config.setClassLoader(applicationContext.getClassLoader());

        config.getSerializationConfig().addSerializerConfig(createPathSerializerConfig());

        // Configure all Hazelcast properties, but do not connect yet to other instances
        if (registration.isEmpty()) {
            log.info("No discovery service is set up, Hazelcast cannot create a multi-node cluster.");
            hazelcastBindOnlyOnInterface("127.0.0.1", config);
        }
        else {
            // The serviceId is by default the application's name,
            // see the "spring.application.name" standard Spring property
            String serviceId = registration.get().getServiceId();
            log.info("Configuring Hazelcast clustering for instanceId: {}", serviceId);

            // Bind to the interface specified in the config if the value is set
            if (hazelcastInterface != null && !hazelcastInterface.isEmpty()) {
                // We should not prefer IPv4, this will ensure that both IPv4 and IPv6 work as none is preferred
                System.setProperty("hazelcast.prefer.ipv4.stack", "false");
                hazelcastBindOnlyOnInterface(hazelcastInterface, config);
            }
            else {
                log.info("Binding Hazelcast to default interface");
                hazelcastBindOnlyOnInterface("127.0.0.1", config);
            }

            // In the local setting (e.g. for development), everything goes through 127.0.0.1, with a different port
            if (hazelcastLocalInstances) {
                log.info("Application is running with the \"localInstances\" setting, Hazelcast cluster will only work with localhost instances");

                // In the local configuration, the hazelcast port is the http-port + the hazelcastPort as offset
                config.getNetworkConfig().setPort(serverProperties.getPort() + hazelcastPort); // Own port
                registration.get().getMetadata().put("hazelcast.port", String.valueOf(serverProperties.getPort() + hazelcastPort));
            }
            else { // Production configuration, one host per instance all using the configured port
                config.setClusterName("prod");
                config.setInstanceName(instanceName);
                config.getNetworkConfig().setPort(hazelcastPort); // Own port
                registration.get().getMetadata().put("hazelcast.port", String.valueOf(hazelcastPort));
            }
        }

        config.getMapConfigs().put("default", initializeDefaultMapConfig(jHipsterProperties));
        config.getMapConfigs().put("files", initializeFilesMapConfig(jHipsterProperties));
        config.getMapConfigs().put("de.tum.cit.aet.artemis.*.domain.*", initializeDomainMapConfig(jHipsterProperties));

        // Configure split brain protection if the cluster was split at some point
        var splitBrainProtectionConfig = new SplitBrainProtectionConfig();
        splitBrainProtectionConfig.setName("artemis-split-brain-protection");
        splitBrainProtectionConfig.setEnabled(true);
        splitBrainProtectionConfig.setMinimumClusterSize(2);
        config.setSplitBrainProtectionConfigs(new ConcurrentHashMap<>());
        config.addSplitBrainProtectionConfig(splitBrainProtectionConfig);
        // Specify when the first run of the split brain protection should be executed (in seconds)
        ClusterProperty.MERGE_FIRST_RUN_DELAY_SECONDS.setSystemProperty("120");

        // only add the queue config if the profile "localci" is active
        Collection<String> activeProfiles = Arrays.asList(env.getActiveProfiles());
        if (activeProfiles.contains(PROFILE_LOCALCI) || activeProfiles.contains(PROFILE_BUILDAGENT)) {
            // add queue config for local ci shared queue
            configureQueueCluster(config, jHipsterProperties);
        }

        // build agents should not hold partitions and only be a lite member
        if (!activeProfiles.contains(PROFILE_TEST_BUILDAGENT) && !activeProfiles.contains(PROFILE_CORE) && activeProfiles.contains(PROFILE_BUILDAGENT)) {
            log.info("Joining cluster as lite member");
            config.setLiteMember(true);
        }

        return Hazelcast.newHazelcastInstance(config);
    }

    /**
     * Binds the Hazelcast instance strictly to the given network interface by setting it
     * as the local and public address. This ensures that Hazelcast does not bind to or listen on
     * unintended interfaces, preventing undesired cluster formation or exposure.
     *
     * <p>
     * Additionally, this method sets internal Hazelcast system properties to disable
     * fallback bindings to any available interface (server/client), enforcing strict network boundaries.
     *
     * @param hazelcastInterface the IP address or hostname of the network interface to bind to (e.g. {@code "127.0.0.1"} or {@code "eth0"})
     * @param config             the Hazelcast configuration object to apply the network settings to
     */
    private void hazelcastBindOnlyOnInterface(String hazelcastInterface, Config config) {
        // Hazelcast should bind to the interface and use it as local and public address
        log.debug("Binding Hazelcast to interface {}", hazelcastInterface);
        System.setProperty("hazelcast.local.localAddress", hazelcastInterface);
        System.setProperty("hazelcast.local.publicAddress", hazelcastInterface);
        config.getNetworkConfig().getInterfaces().setEnabled(true).setInterfaces(Collections.singleton(hazelcastInterface));

        // Hazelcast should only bind to the interface provided, not to any interface
        config.setProperty("hazelcast.socket.bind.any", "false");
        config.setProperty("hazelcast.socket.server.bind.any", "false");
        config.setProperty("hazelcast.socket.client.bind.any", "false");
    }

    /**
     * Configures a shared job queue named {@code buildJobQueue} for synchronizing tasks
     * between nodes in a local continuous integration (CI) setup using Hazelcast.
     *
     * <p>
     * This queue is configured with a backup count to ensure fault tolerance and a
     * priority-based comparator to control job scheduling order. It is only activated
     * when specific profiles (e.g., {@code localci}, {@code buildagent}) are enabled.
     *
     * @param config             the Hazelcast configuration to which the queue configuration will be added
     * @param jHipsterProperties the JHipster properties used to extract cache-related parameters such as backup count
     */
    private void configureQueueCluster(Config config, JHipsterProperties jHipsterProperties) {
        // Queue specific configurations
        log.debug("Configure Build Job Queue synchronization in Hazelcast for Local CI");
        QueueConfig queueConfig = new QueueConfig("buildJobQueue");
        queueConfig.setBackupCount(jHipsterProperties.getCache().getHazelcast().getBackupCount());
        queueConfig.setPriorityComparatorClassName(LocalCIPriorityQueueComparator.class.getName());
        config.addQueueConfig(queueConfig);
    }

    /**
     * Note: this is configured to be able to cache files in the Hazelcast cluster, see {@link FileService#getFileForPath}
     *
     * @return the serializer config for files based on paths
     */
    private SerializerConfig createPathSerializerConfig() {
        SerializerConfig serializerConfig = new SerializerConfig();
        serializerConfig.setTypeClass(Path.class);
        serializerConfig.setImplementation(new HazelcastPathSerializer());
        return serializerConfig;
    }

    @Bean
    public KeyGenerator keyGenerator() {
        return new PrefixedKeyGenerator(this.gitProperties.orElse(null), this.buildProperties.orElse(null));
    }

    // config for files in the files system
    private MapConfig initializeFilesMapConfig(JHipsterProperties jHipsterProperties) {
        return new MapConfig().setBackupCount(jHipsterProperties.getCache().getHazelcast().getBackupCount())
                .setEvictionConfig(new EvictionConfig().setEvictionPolicy(EvictionPolicy.LRU).setMaxSizePolicy(MaxSizePolicy.PER_NODE))
                .setTimeToLiveSeconds(jHipsterProperties.getCache().getHazelcast().getTimeToLiveSeconds());
    }

    // default config, if nothing specific is defined
    private MapConfig initializeDefaultMapConfig(JHipsterProperties jHipsterProperties) {
        return new MapConfig()
                // Number of backups. If 1 is set as the backup-count e.g., then all entries of the map will be copied to another JVM for fail-safety. Valid numbers are 0 (no
                // backup), 1, 2, 3. While we store most of the data in the database, we might use the backup for live quiz exercises and their corresponding hazelcast hash maps
                .setBackupCount(jHipsterProperties.getCache().getHazelcast().getBackupCount())
                .setEvictionConfig(new EvictionConfig().setEvictionPolicy(EvictionPolicy.LRU).setMaxSizePolicy(MaxSizePolicy.PER_NODE));
    }

    // config for all domain object, i.e. entities such as Course, Exercise, etc.
    private MapConfig initializeDomainMapConfig(JHipsterProperties jHipsterProperties) {
        return new MapConfig().setTimeToLiveSeconds(jHipsterProperties.getCache().getHazelcast().getTimeToLiveSeconds());
    }
}
