package de.tum.in.www1.artemis.usermanagement.service.messaging;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * This service is only present on a node that runs the 'scheduling' profile.
 * As this node can handle all the processing without interaction with another node, everything is handled locally (without Hazelcast).
 */
@Service
@Profile("scheduling")
public class MainInstanceMessageSendService implements InstanceMessageSendService {

    public InstanceMessageReceiveService instanceMessageReceiveService;

    public MainInstanceMessageSendService(InstanceMessageReceiveService instanceMessageReceiveService) {
        this.instanceMessageReceiveService = instanceMessageReceiveService;
    }

    @Override
    public void sendRemoveNonActivatedUserSchedule(Long userId) {
        // No need to go through the broker, pass it directly
        instanceMessageReceiveService.processRemoveNonActivatedUser(userId);
    }

    @Override
    public void sendCancelRemoveNonActivatedUserSchedule(Long userId) {
        // No need to go through the broker, pass it directly
        instanceMessageReceiveService.processCancelRemoveNonActivatedUser(userId);
    }
}
