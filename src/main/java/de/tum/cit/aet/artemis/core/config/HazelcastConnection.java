package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.scheduling.annotation.Scheduled;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;

@Profile({ PROFILE_BUILDAGENT, PROFILE_CORE })
@Lazy
@Configuration
public class HazelcastConnection {

    private static final Logger log = LoggerFactory.getLogger(HazelcastConnection.class);

    // the discovery service client that connects against the registration (jhipster registry) so that multiple server nodes can find each other to synchronize using Hazelcast
    private final DiscoveryClient discoveryClient;

    // the service registry, in our current deployment this is the jhipster registry which offers a Eureka Server under the hood
    @Nullable
    private final Registration registration;

    private final Environment env;

    @Value("${spring.hazelcast.port:5701}")
    private int hazelcastPort;

    @Value("${spring.jpa.properties.hibernate.cache.hazelcast.instance_name}")
    private String instanceName;

    public HazelcastConnection(DiscoveryClient discoveryClient, @Autowired(required = false) @Nullable Registration registration, Environment env) {
        this.discoveryClient = discoveryClient;
        this.registration = registration;
        this.env = env;
    }

    /**
     * This scheduled task regularly checks if all members of the Hazelcast cluster are connected to each other.
     * This is one countermeasure to a split cluster.
     */
    @Scheduled(fixedRate = 2, initialDelay = 1, timeUnit = TimeUnit.MINUTES)
    public void connectToAllMembers() {
        if (registration == null || env.acceptsProfiles(Profiles.of(SPRING_PROFILE_TEST))) {
            return;
        }
        var thisHazelcastInstance = Hazelcast.getHazelcastInstanceByName(instanceName);
        if (thisHazelcastInstance == null) {
            log.warn("Hazelcast instance not found, cannot connect to cluster members");
            return;
        }

        var hazelcastMemberAddresses = thisHazelcastInstance.getCluster().getMembers().stream().map(member -> {
            try {
                return member.getAddress().getInetAddress().getHostAddress();
            }
            catch (UnknownHostException e) {
                return "unknown";
            }
        }).toList();

        var instances = discoveryClient.getInstances(registration.getServiceId());
        log.debug("Current {} Registry members: {}", instances.size(), instances.stream().map(ServiceInstance::getHost).toList());
        log.debug("Current {} Hazelcast members: {}", hazelcastMemberAddresses.size(), hazelcastMemberAddresses);

        for (ServiceInstance instance : instances) {
            // Workaround for IPv6 addresses, as they are enclosed in brackets
            var instanceHostClean = instance.getHost().replace("[", "").replace("]", "");
            if (hazelcastMemberAddresses.stream().noneMatch(member -> member.equals(instanceHostClean))) {
                addHazelcastClusterMember(instance, thisHazelcastInstance.getConfig());
            }
        }
    }

    @EventListener(FullStartupEvent.class)
    private void connectHazelcast() {
        var hazelcastInstance = Hazelcast.getHazelcastInstanceByName(instanceName);
        var config = hazelcastInstance.getConfig();

        if (registration == null) {
            // If there is no registration, we are not running in a clustered environment and cannot connect Hazelcast nodes.
            return;
        }
        String serviceId = registration.getServiceId();
        for (ServiceInstance instance : discoveryClient.getInstances(serviceId)) {
            addHazelcastClusterMember(instance, config);
        }
    }

    private void addHazelcastClusterMember(ServiceInstance instance, Config config) {
        var clusterMemberPort = instance.getMetadata().getOrDefault("hazelcast.port", String.valueOf(hazelcastPort));
        var clusterMemberAddress = instance.getHost() + ":" + clusterMemberPort; // Address where the other instance is expected
        log.info("Adding Hazelcast cluster member {}", clusterMemberAddress);
        config.getNetworkConfig().getJoin().getTcpIpConfig().addMember(clusterMemberAddress);
    }
}
