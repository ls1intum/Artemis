package de.tum.in.www1.artemis.service.messaging;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;

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
    public void sendProgrammingExerciseSchedule(Long exerciseId) {
        log.info("Sending schedule for programming exercise {} to broker.", exerciseId);
        sendMessageDelayed("programming-exercise-schedule", exerciseId);
    }

    @Override
    public void sendProgrammingExerciseScheduleCancel(Long exerciseId) {
        log.info("Sending schedule cancel for programming exercise {} to broker.", exerciseId);
        sendMessageDelayed("programming-exercise-schedule-cancel", exerciseId);
    }

    @Override
    public void sendModelingExerciseSchedule(Long exerciseId) {
        log.info("Sending schedule for modeling exercise {} to broker.", exerciseId);
        sendMessageDelayed("modeling-exercise-schedule", exerciseId);
    }

    @Override
    public void sendModelingExerciseScheduleCancel(Long exerciseId) {
        log.info("Sending schedule cancel for modeling exercise {} to broker.", exerciseId);
        sendMessageDelayed("modeling-exercise-schedule-cancel", exerciseId);
    }

    @Override
    public void sendModelingExerciseInstantClustering(Long exerciseId) {
        log.info("Sending schedule instant clustering for modeling exercise {} to broker.", exerciseId);
        sendMessageDelayed("modeling-exercise-schedule-instant-clustering", exerciseId);
    }

    @Override
    public void sendTextExerciseSchedule(Long exerciseId) {
        log.info("Sending schedule for text exercise {} to broker.", exerciseId);
        sendMessageDelayed("text-exercise-schedule", exerciseId);
    }

    @Override
    public void sendTextExerciseScheduleCancel(Long exerciseId) {
        log.info("Sending schedule cancel for text exercise {} to broker.", exerciseId);
        sendMessageDelayed("text-exercise-schedule-cancel", exerciseId);
    }

    @Override
    public void sendTextExerciseInstantClustering(Long exerciseId) {
        log.info("Sending schedule instant clustering for text exercise {} to broker.", exerciseId);
        sendMessageDelayed("text-exercise-schedule-instant-clustering", exerciseId);
    }

    @Override
    public void sendUnlockAllRepositories(Long exerciseId) {
        log.info("Sending unlock all repositories for programming exercise {} to broker.", exerciseId);
        sendMessageDelayed("programming-exercise-unlock-repositories", exerciseId);
    }

    @Override
    public void sendLockAllRepositories(Long exerciseId) {
        log.info("Sending lock all repositories for programming exercise {} to broker.", exerciseId);
        sendMessageDelayed("programming-exercise-lock-repositories", exerciseId);
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

    @Override
    public void sendExerciseReleaseNotificationSchedule(Long exerciseId) {
        log.info("Sending prepare notification for exercise {} to broker.", exerciseId);
        sendMessageDelayed("exercise-notification-schedule", exerciseId);
    }

    private void sendMessageDelayed(String destination, Long exerciseId) {
        exec.schedule(() -> hazelcastInstance.getTopic(destination).publish(exerciseId), 1, TimeUnit.SECONDS);
    }
}
