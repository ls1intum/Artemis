package de.tum.in.www1.artemis.usermanagement.service.messaging;

/**
 * This interface offers a service that will send messages to the node that runs the scheduling.
 * This can either be the same node or a different node within the Hazelcast cluster.
 */
public interface InstanceMessageSendService {

    /**
     * Send a message to the main server that the non-activated should be deleted.
     * @param userId the user id of the non-activated user
     */
    void sendRemoveNonActivatedUserSchedule(Long userId);

    /**
     * Send a message to the main server that cancels the schedule to remove the non-activated user.
     * @param userId the user id of the non-activated user
     */
    void sendCancelRemoveNonActivatedUserSchedule(Long userId);
}
