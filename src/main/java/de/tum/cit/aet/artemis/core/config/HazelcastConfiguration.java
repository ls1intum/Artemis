package de.tum.cit.aet.artemis.core.config;

import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.net.UnknownHostException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.scheduling.annotation.Scheduled;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;

@Configuration
@Lazy
public class HazelcastConfiguration {

    private static final Logger log = LoggerFactory.getLogger(HazelcastConfiguration.class);

    // the discovery service client that connects against the registration (jhipster registry) so that multiple server nodes can find each other to synchronize using Hazelcast
    private final DiscoveryClient discoveryClient;

    // the service registry, in our current deployment this is the jhipster registry which offers a Eureka Server under the hood
    @Nullable
    private final Registration registration;

    private final ServerProperties serverProperties;

    private final Environment env;

    @Value("${spring.hazelcast.interface:}")
    private String hazelcastInterface;

    @Value("${spring.hazelcast.port:5701}")
    private int hazelcastPort;

    @Value("${spring.hazelcast.localInstances:true}")
    private boolean hazelcastLocalInstances;

    @Value("${spring.jpa.properties.hibernate.cache.hazelcast.instance_name}")
    private String instanceName;

    public HazelcastConfiguration(DiscoveryClient discoveryClient, @Autowired(required = false) @Nullable Registration registration, ServerProperties serverProperties,
            Environment env) {
        this.discoveryClient = discoveryClient;
        this.registration = registration;
        this.serverProperties = serverProperties;
        this.env = env;
    }

    /**
     * This scheduled task regularly checks if all members of the Hazelcast cluster are connected to each other.
     * This is one countermeasure to a split cluster.
     */
    @Scheduled(fixedRate = 2, initialDelay = 1, timeUnit = TimeUnit.MINUTES)
    public void connectToAllMembers() {
        if (env.acceptsProfiles(Profiles.of(SPRING_PROFILE_TEST))) {
            return;
        }
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

    @EventListener(FullStartupEvent.class)
    private void connectHazelcast() {
        var hazelcastInstance = Hazelcast.getHazelcastInstanceByName(instanceName);
        var config = hazelcastInstance.getConfig();

        if (registration == null) {
            log.info("No discovery service is set up, Hazelcast cannot create a multi-node cluster.");
            hazelcastBindOnlyOnInterface("127.0.0.1", config);
        }
        else {
            // The serviceId is by default the application's name,
            // see the "spring.application.name" standard Spring property
            String serviceId = registration.getServiceId();
            log.info("Configuring Hazelcast clustering for serviceId {}, instanceId {} and instanceName {}", serviceId, registration.getInstanceId(), instanceName);

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
}
