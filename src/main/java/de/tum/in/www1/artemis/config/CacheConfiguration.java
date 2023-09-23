package de.tum.in.www1.artemis.config;

import java.nio.file.Path;
import java.util.Collections;

import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

import com.hazelcast.config.*;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.spring.context.SpringManagedContext;

import de.tum.in.www1.artemis.service.HazelcastPathSerializer;
import de.tum.in.www1.artemis.service.scheduled.cache.quiz.QuizScheduleService;
import tech.jhipster.config.JHipsterProperties;
import tech.jhipster.config.cache.PrefixedKeyGenerator;

@Configuration
@EnableCaching
public class CacheConfiguration {

    private final Logger log = LoggerFactory.getLogger(CacheConfiguration.class);

    private GitProperties gitProperties;

    private BuildProperties buildProperties;

    private final ServerProperties serverProperties;

    private final DiscoveryClient discoveryClient;

    private Registration registration;

    private final ApplicationContext applicationContext;

    @Value("${spring.jpa.properties.hibernate.cache.hazelcast.instance_name}")
    private String instanceName;

    @Value("${spring.hazelcast.interface:}")
    private String hazelcastInterface;

    @Value("${spring.hazelcast.port:5701}")
    private int hazelcastPort;

    @Value("${spring.hazelcast.localInstances:true}")
    private boolean hazelcastLocalInstances;

    public CacheConfiguration(ServerProperties serverProperties, DiscoveryClient discoveryClient, ApplicationContext applicationContext) {
        this.serverProperties = serverProperties;
        this.discoveryClient = discoveryClient;
        this.applicationContext = applicationContext;
    }

    @Autowired(required = false) // ok
    public void setRegistration(Registration registration) {
        this.registration = registration;
    }

    @PreDestroy
    public void destroy() {
        log.info("Closing Cache Manager");
        Hazelcast.shutdownAll();
    }

    @Bean
    public CacheManager cacheManager(HazelcastInstance hazelcastInstance) {
        log.debug("Starting HazelcastCacheManager");
        return new com.hazelcast.spring.cache.HazelcastCacheManager(hazelcastInstance);
    }

    /**
     * Setup the hazelcast instance based on the given jHipster properties and the enabled spring profiles.
     *
     * @param jHipsterProperties the jhipster properties
     * @return the created HazelcastInstance
     */
    @Bean
    public HazelcastInstance hazelcastInstance(JHipsterProperties jHipsterProperties) {
        log.debug("Configuring Hazelcast");
        HazelcastInstance hazelCastInstance = Hazelcast.getHazelcastInstanceByName("Artemis");
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
            log.warn("No discovery service is set up, Hazelcast cannot create a cluster.");
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
                for (ServiceInstance instance : discoveryClient.getInstances(serviceId)) {
                    String clusterMember = instance.getHost() + ":" + (instance.getPort() + hazelcastPort); // Address where the other instance is expected
                    log.info("Adding Hazelcast (dev) cluster member {}", clusterMember);
                    config.getNetworkConfig().getJoin().getTcpIpConfig().addMember(clusterMember);
                }
            }
            else { // Production configuration, one host per instance all using the configured port
                config.setClusterName("prod");
                config.setInstanceName(instanceName);
                config.getNetworkConfig().setPort(hazelcastPort); // Own port
                for (ServiceInstance instance : discoveryClient.getInstances(serviceId)) {
                    String clusterMember = instance.getHost() + ":" + hazelcastPort; // Address where the other instance is expected
                    log.info("Adding Hazelcast (prod) cluster member {}", clusterMember);
                    config.getNetworkConfig().getJoin().getTcpIpConfig().addMember(clusterMember);
                }
            }
        }
        config.getMapConfigs().put("default", initializeDefaultMapConfig(jHipsterProperties));
        config.getMapConfigs().put("de.tum.in.www1.artemis.domain.*", initializeDomainMapConfig(jHipsterProperties));

        QuizScheduleService.configureHazelcast(config);
        return Hazelcast.newHazelcastInstance(config);
    }

    private void hazelcastBindOnlyOnInterface(String hazelcastInterface, Config config) {
        // Hazelcast should bind to the interface and use it as local and public address
        log.info("Binding Hazelcast to interface {}", hazelcastInterface);
        System.setProperty("hazelcast.local.localAddress", hazelcastInterface);
        System.setProperty("hazelcast.local.publicAddress", hazelcastInterface);
        config.getNetworkConfig().getInterfaces().setEnabled(true).setInterfaces(Collections.singleton(hazelcastInterface));

        // Hazelcast should only bind to the interface provided, not to any interface
        config.setProperty("hazelcast.socket.bind.any", "false");
        config.setProperty("hazelcast.socket.server.bind.any", "false");
        config.setProperty("hazelcast.socket.client.bind.any", "false");
    }

    private SerializerConfig createPathSerializerConfig() {
        SerializerConfig serializerConfig = new SerializerConfig();
        serializerConfig.setTypeClass(Path.class);
        serializerConfig.setImplementation(new HazelcastPathSerializer());
        return serializerConfig;
    }

    @Autowired(required = false) // ok
    public void setGitProperties(GitProperties gitProperties) {
        this.gitProperties = gitProperties;
    }

    @Autowired(required = false) // ok
    public void setBuildProperties(BuildProperties buildProperties) {
        this.buildProperties = buildProperties;
    }

    @Bean
    public KeyGenerator keyGenerator() {
        return new PrefixedKeyGenerator(this.gitProperties, this.buildProperties);
    }

    private MapConfig initializeDefaultMapConfig(JHipsterProperties jHipsterProperties) {
        MapConfig mapConfig = new MapConfig();

        /*
         * Number of backups. If 1 is set as the backup-count for example, then all entries of the map will be copied to another JVM for fail-safety. Valid numbers are 0 (no
         * backup), 1, 2, 3. While we store most of the data in the database, we might use the backup for live quiz exercises and their corresponding hazelcast hash maps
         */
        mapConfig.setBackupCount(jHipsterProperties.getCache().getHazelcast().getBackupCount());

        /*
         * Valid values are: NONE (no eviction), LRU (Least Recently Used), LFU (Least Frequently Used). LRU is the default.
         */
        mapConfig.setEvictionConfig(new EvictionConfig().setEvictionPolicy(EvictionPolicy.LRU).setMaxSizePolicy(MaxSizePolicy.PER_NODE));
        return mapConfig;
    }

    private MapConfig initializeDomainMapConfig(JHipsterProperties jHipsterProperties) {
        MapConfig mapConfig = new MapConfig();
        mapConfig.setTimeToLiveSeconds(jHipsterProperties.getCache().getHazelcast().getTimeToLiveSeconds());
        return mapConfig;
    }
}
