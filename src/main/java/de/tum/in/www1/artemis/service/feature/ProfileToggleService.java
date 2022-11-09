package de.tum.in.www1.artemis.service.feature;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.hazelcast.cluster.InitialMembershipEvent;
import com.hazelcast.cluster.InitialMembershipListener;
import com.hazelcast.cluster.Member;
import com.hazelcast.cluster.MembershipEvent;
import com.hazelcast.core.HazelcastInstance;

import de.tum.in.www1.artemis.service.WebsocketMessagingService;

@Service
public class ProfileToggleService {

    private final Logger log = LoggerFactory.getLogger(ProfileToggleService.class);

    private static final String TOPIC_PROFILE_TOGGLES = "/topic/management/profile-toggles";

    private final WebsocketMessagingService websocketMessagingService;

    private final HazelcastInstance hazelcastInstance;

    public ProfileToggleService(WebsocketMessagingService websocketMessagingService, HazelcastInstance hazelcastInstance) {
        this.websocketMessagingService = websocketMessagingService;
        this.hazelcastInstance = hazelcastInstance;

        hazelcastInstance.getCluster().addMembershipListener(new InitialMembershipListener() {

            @Override
            public void init(InitialMembershipEvent event) {
                sendUpdate(event.getMembers());
            }

            @Override
            public void memberAdded(MembershipEvent membershipEvent) {
                sendUpdate(membershipEvent.getMembers());
            }

            @Override
            public void memberRemoved(MembershipEvent membershipEvent) {
                sendUpdate(membershipEvent.getMembers());
            }
        });
    }

    private void sendUpdate(Set<Member> members) {
        log.debug("Sending membership update");
        websocketMessagingService.sendMessage(TOPIC_PROFILE_TOGGLES, enabledProfiles(members));
    }

    /**
     * Get all profiles that are currently enabled on the system
     *
     * @return A list of enabled profiles (on the whole system)
     */
    private Set<String> enabledProfiles(Set<Member> members) {
        return members.stream().flatMap(member -> Stream.of(member.getAttributes().getOrDefault("profiles", "").split(","))).collect(Collectors.toSet());
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
