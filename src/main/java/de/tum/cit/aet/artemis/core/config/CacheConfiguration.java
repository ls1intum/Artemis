package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.Nullable;
import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;

import com.hazelcast.config.Config;
import com.hazelcast.config.EvictionConfig;
import com.hazelcast.config.EvictionPolicy;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.MaxSizePolicy;
import com.hazelcast.config.QueueConfig;
import com.hazelcast.config.SerializerConfig;
import com.hazelcast.config.SplitBrainProtectionConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.spi.properties.ClusterProperty;
import com.hazelcast.spring.cache.HazelcastCacheManager;
import com.hazelcast.spring.context.SpringManagedContext;

import de.tum.cit.aet.artemis.service.HazelcastPathSerializer;
import de.tum.cit.aet.artemis.service.connectors.localci.LocalCIPriorityQueueComparator;
import tech.jhipster.config.JHipsterProperties;
import tech.jhipster.config.cache.PrefixedKeyGenerator;

@Profile({ PROFILE_CORE, PROFILE_BUILDAGENT })
@Configuration
@EnableCaching
public class CacheConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CacheConfiguration.class);

    @Nullable
    private final GitProperties gitProperties;

    @Nullable
    private final BuildProperties buildProperties;

    private final ServerProperties serverProperties;

    // the discovery service client that connects against the registration (jhipster registry) so that multiple server nodes can find each other to synchronize using Hazelcast
    private final DiscoveryClient discoveryClient;

    // the service registry, in our current deployment this is the jhipster registry which offers a Eureka Server under the hood
    @Nullable
    private final Registration registration;

    private final ApplicationContext applicationContext;

    private final Environment env;

    @Value("${spring.jpa.properties.hibernate.cache.hazelcast.instance_name}")
    private String instanceName;

    @Value("${spring.hazelcast.interface:}")
    private String hazelcastInterface;

    @Value("${spring.hazelcast.port:5701}")
    private int hazelcastPort;

    @Value("${spring.hazelcast.localInstances:true}")
    private boolean hazelcastLocalInstances;

    // NOTE: the registration is optional
    public CacheConfiguration(ServerProperties serverProperties, DiscoveryClient discoveryClient, ApplicationContext applicationContext,
            @Autowired(required = false) @Nullable Registration registration, @Autowired(required = false) @Nullable GitProperties gitProperties,
            @Autowired(required = false) @Nullable BuildProperties buildProperties, Environment env) {
        this.serverProperties = serverProperties;
        this.discoveryClient = discoveryClient;
        this.applicationContext = applicationContext;
        this.registration = registration;
        this.gitProperties = gitProperties;
        this.buildProperties = buildProperties;
        this.env = env;
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
     * This scheduled task regularly checks if all members of the Hazelcast cluster are connected to each other.
     * This is one countermeasure to a split cluster.
     */
    @Scheduled(fixedRate = 2, initialDelay = 1, timeUnit = TimeUnit.MINUTES)
    public void connectToAllMembers() {
        if (registration == null) {
            return;
        }
        String serviceId = registration.getServiceId();
        var hazelcastInstance = Hazelcast.getHazelcastInstanceByName(instanceName);
        if (hazelcastInstance == null) {
            log.warn("Hazelcast instance not found, cannot connect to cluster members");
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

        log.debug("Current Registry members: {}", discoveryClient.getInstances(serviceId).stream().map(ServiceInstance::getHost).toList());
        log.debug("Current Hazelcast members: {}", hazelcastMemberAddresses);

        for (ServiceInstance instance : discoveryClient.getInstances(serviceId)) {
            var instanceHost = instance.getHost();
            // Workaround for IPv6 addresses, as they are enclosed in brackets
            var instanceHostClean = instanceHost.replace("[", "").replace("]", "");
            if (hazelcastMemberAddresses.stream().noneMatch(member -> member.equals(instanceHostClean))) {
                var clusterMemberPort = instance.getMetadata().getOrDefault("hazelcast.port", String.valueOf(hazelcastPort));
                var clusterMemberAddress = instanceHost + ":" + clusterMemberPort;
                log.info("Adding Hazelcast cluster member {}", clusterMemberAddress);
                hazelcastInstance.getConfig().getNetworkConfig().getJoin().getTcpIpConfig().addMember(clusterMemberAddress);
            }
        }
    }

    /**
     * Setup the hazelcast instance based on the given jHipster properties and the enabled spring profiles.
     *
     * @param jHipsterProperties the jhipster properties
     * @return the created HazelcastInstance
     */
    @Bean(name = "hazelcastInstance")
    public HazelcastInstance hazelcastInstance(JHipsterProperties jHipsterProperties) {
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

        if (registration == null) {
            log.info("No discovery service is set up, Hazelcast cannot create a multi-node cluster.");
            hazelcastBindOnlyOnInterface("127.0.0.1", config);
        }
        else {
            // The serviceId is by default the application's name,
            // see the "spring.application.name" standard Spring property
            String serviceId = registration.getServiceId();
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
                registration.getMetadata().put("hazelcast.port", String.valueOf(serverProperties.getPort() + hazelcastPort));

                for (ServiceInstance instance : discoveryClient.getInstances(serviceId)) {
                    var clusterMemberPort = instance.getMetadata().getOrDefault("hazelcast.port", String.valueOf(serverProperties.getPort() + hazelcastPort));
                    String clusterMemberAddress = instance.getHost() + ":" + clusterMemberPort; // Address where the other instance is expected
                    log.info("Adding Hazelcast (dev) cluster member {}", clusterMemberAddress);
                    config.getNetworkConfig().getJoin().getTcpIpConfig().addMember(clusterMemberAddress);
                }
            }
            else { // Production configuration, one host per instance all using the configured port
                config.setClusterName("prod");
                config.setInstanceName(instanceName);
                config.getNetworkConfig().setPort(hazelcastPort); // Own port
                registration.getMetadata().put("hazelcast.port", String.valueOf(hazelcastPort));

                for (ServiceInstance instance : discoveryClient.getInstances(serviceId)) {
                    var clusterMemberPort = instance.getMetadata().getOrDefault("hazelcast.port", String.valueOf(hazelcastPort));
                    String clusterMemberAddress = instance.getHost() + ":" + clusterMemberPort; // Address where the other instance is expected
                    log.info("Adding Hazelcast (prod) cluster member {}", clusterMemberAddress);
                    config.getNetworkConfig().getJoin().getTcpIpConfig().addMember(clusterMemberAddress);
                }
            }
        }
        config.getMapConfigs().put("default", initializeDefaultMapConfig(jHipsterProperties));
        config.getMapConfigs().put("files", initializeFilesMapConfig(jHipsterProperties));
        // TODO: add all future domain paths here
        config.getMapConfigs().put("de.tum.cit.aet.artemis.domain.*", initializeDomainMapConfig(jHipsterProperties));

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
        if (!activeProfiles.contains(PROFILE_CORE) && activeProfiles.contains(PROFILE_BUILDAGENT)) {
            log.info("Joining cluster as lite member");
            config.setLiteMember(true);
        }

        return Hazelcast.newHazelcastInstance(config);
    }

    private void configureQueueCluster(Config config, JHipsterProperties jHipsterProperties) {
        // Queue specific configurations
        log.debug("Configure Build Job Queue synchronization in Hazelcast for Local CI");
        QueueConfig queueConfig = new QueueConfig("buildJobQueue");
        queueConfig.setBackupCount(jHipsterProperties.getCache().getHazelcast().getBackupCount());
        queueConfig.setPriorityComparatorClassName(LocalCIPriorityQueueComparator.class.getName());
        config.addQueueConfig(queueConfig);
    }

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
     * Note: this is configured to be able to cache files in the Hazelcast cluster, see {@link de.tum.cit.aet.artemis.service.FileService#getFileForPath}
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
        return new PrefixedKeyGenerator(this.gitProperties, this.buildProperties);
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
