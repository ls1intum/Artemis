package de.tum.in.www1.artemis.usermanagement.service.messaging;

import com.hazelcast.core.HazelcastInstance;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.usermanagement.service.scheduled.UserScheduleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * This service is only available on a node with the 'scheduling' profile.
 * It receives messages from Hazelcast whenever another node sends a message to a specific topic and processes it on this node.
 */
@Service
@Profile("scheduling")
public class InstanceMessageReceiveService {

    private static final String USER_MANAGEMENT_REMOVE_NON_ACTIVATED_USER_TOPIC = "user-management-remove-non-activated-user";
    private static final String USER_MANAGEMENT_CANCEL_REMOVE_NON_ACTIVATED_USER_TOPIC = "user-management-cancel-remove-non-activated-user";

    private final Logger log = LoggerFactory.getLogger(InstanceMessageReceiveService.class);

    private final UserRepository userRepository;

    private final UserScheduleService userScheduleService;

    public InstanceMessageReceiveService(HazelcastInstance hazelcastInstance, UserRepository userRepository, UserScheduleService userScheduleService) {
        this.userRepository = userRepository;
        this.userScheduleService = userScheduleService;

        hazelcastInstance.<Long>getTopic(USER_MANAGEMENT_REMOVE_NON_ACTIVATED_USER_TOPIC).addMessageListener(message -> {
            SecurityUtils.setAuthorizationObject();
            processRemoveNonActivatedUser((message.getMessageObject()));
        });
        hazelcastInstance.<Long>getTopic(USER_MANAGEMENT_CANCEL_REMOVE_NON_ACTIVATED_USER_TOPIC).addMessageListener(message -> {
            SecurityUtils.setAuthorizationObject();
            processCancelRemoveNonActivatedUser((message.getMessageObject()));
        });
    }

    public void processRemoveNonActivatedUser(Long userId) {
        log.info("Received remove non-activated user for user {}", userId);
        User user = userRepository.findByIdWithGroupsAndAuthoritiesElseThrow(userId);
        userScheduleService.scheduleForRemoveNonActivatedUser(user);
    }

    public void processCancelRemoveNonActivatedUser(Long userId) {
        log.info("Received cancel removal of non-activated user for user {}", userId);
        User user = userRepository.findByIdWithGroupsAndAuthoritiesElseThrow(userId);
        userScheduleService.cancelScheduleRemoveNonActivatedUser(user);
    }
}
