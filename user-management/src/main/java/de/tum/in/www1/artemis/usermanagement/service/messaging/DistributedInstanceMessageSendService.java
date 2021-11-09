package de.tum.in.www1.artemis.usermanagement.service.messaging;

import com.hazelcast.core.HazelcastInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * This service is only active on a node that does not run with the 'scheduling' profile.
 * All requests are forwarded to a Hazelcast topic and a node with the 'scheduling' profile will then process it.
 */
@Service
@Profile("!scheduling")
public class DistributedInstanceMessageSendService implements InstanceMessageSendService {

    private final Logger log = LoggerFactory.getLogger(DistributedInstanceMessageSendService.class);

    private final ScheduledExecutorService exec = Executors.newScheduledThreadPool(1);

    private final HazelcastInstance hazelcastInstance;

    public DistributedInstanceMessageSendService(HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
    }

    @Override
    public void sendRemoveNonActivatedUserSchedule(Long userId) {
        log.info("Sending remove non-activated user {} to broker.", userId);
        sendMessageDelayed("user-management-remove-non-activated-user", userId);
    }

    @Override
    public void sendCancelRemoveNonActivatedUserSchedule(Long userId) {
        log.info("Sending cancel removal of non-activated user {} to broker.", userId);
        sendMessageDelayed("user-management-cancel-remove-non-activated-user", userId);
    }

    private void sendMessageDelayed(String destination, Long exerciseId) {
        exec.schedule(() -> hazelcastInstance.getTopic(destination).publish(exerciseId), 1, TimeUnit.SECONDS);
    }
}
