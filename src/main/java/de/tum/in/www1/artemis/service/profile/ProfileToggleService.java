package de.tum.in.www1.artemis.service.profile;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import com.hazelcast.cluster.InitialMembershipEvent;
import com.hazelcast.cluster.InitialMembershipListener;
import com.hazelcast.cluster.Member;
import com.hazelcast.cluster.MembershipEvent;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.in.www1.artemis.service.WebsocketMessagingService;

@Service
public class ProfileToggleService {

    private final Logger log = LoggerFactory.getLogger(ProfileToggleService.class);

    private static final String INSTANCE_PROFILE_MAP_NAME = "instanceProfiles";

    private static final String INSTANCE_READY_TOPIC_NAME = "instanceReady";

    private static final String TOPIC_PROFILE_TOGGLES = "/topic/management/profile-toggles";

    private final WebsocketMessagingService websocketMessagingService;

    private final HazelcastInstance hazelcastInstance;

    private final Environment env;

    private final IMap<UUID, String> instanceProfilesMap;

    public ProfileToggleService(WebsocketMessagingService websocketMessagingService, HazelcastInstance hazelcastInstance, Environment env) {
        this.websocketMessagingService = websocketMessagingService;
        this.hazelcastInstance = hazelcastInstance;
        this.env = env;

        this.instanceProfilesMap = hazelcastInstance.getMap(INSTANCE_PROFILE_MAP_NAME);

        hazelcastInstance.getCluster().addMembershipListener(new InitialMembershipListener() {

            @Override
            public void init(InitialMembershipEvent event) {
            }

            @Override
            public void memberAdded(MembershipEvent membershipEvent) {
            }

            @Override
            public void memberRemoved(MembershipEvent membershipEvent) {
                log.debug("Instance removed event received");
                instanceProfilesMap.remove(membershipEvent.getMember().getUuid());
                sendUpdate(membershipEvent.getMembers());
            }
        });

        hazelcastInstance.<String>getTopic(INSTANCE_READY_TOPIC_NAME).addMessageListener(instanceId -> {
            log.debug("Instance startup event received");
            sendUpdate(hazelcastInstance.getCluster().getMembers());
        });
    }

    /**
     * Update the combined available list of profiles when the instance started successfully.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void publishAvailableProfiles() {
        // Update available profiles for this instance
        instanceProfilesMap.put(hazelcastInstance.getLocalEndpoint().getUuid(), String.join(",", env.getActiveProfiles()));

        // Inform other instances about readyness
        hazelcastInstance.getTopic(INSTANCE_READY_TOPIC_NAME).publish(hazelcastInstance.getLocalEndpoint().getUuid());
    }

    private void sendUpdate(Set<Member> members) {
        log.debug("Sending membership update " + enabledProfiles(members));
        websocketMessagingService.sendMessage(TOPIC_PROFILE_TOGGLES, enabledProfiles(members));
    }

    /**
     * Get all profiles that are currently enabled on the system
     *
     * @return A list of enabled profiles (on the whole system)
     */
    private Set<String> enabledProfiles(Set<Member> members) {
        return members.stream().flatMap(member -> Stream.of(instanceProfilesMap.getOrDefault(member.getUuid(), "").split(","))).collect(Collectors.toSet());
    }

    /**
     * Get all profiles that are currently enabled on the system
     *
     * @return A list of enabled profiles (on the whole system)
     */
    public Set<String> enabledProfiles() {
        return enabledProfiles(hazelcastInstance.getCluster().getMembers());
    }
}
