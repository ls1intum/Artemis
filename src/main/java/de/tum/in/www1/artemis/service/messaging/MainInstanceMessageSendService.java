package de.tum.in.www1.artemis.service.messaging;

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
    public void sendProgrammingExerciseSchedule(Long exerciseId) {
        // No need to go through the broker, pass it directly
        instanceMessageReceiveService.processScheduleProgrammingExercise(exerciseId);
    }

    @Override
    public void sendProgrammingExerciseScheduleCancel(Long exerciseId) {
        // No need to go through the broker, pass it directly
        instanceMessageReceiveService.processScheduleProgrammingExerciseCancel(exerciseId);
    }

    @Override
    public void sendModelingExerciseSchedule(Long exerciseId) {
        // No need to go through the broker, pass it directly
        instanceMessageReceiveService.processScheduleModelingExercise(exerciseId);
    }

    @Override
    public void sendModelingExerciseScheduleCancel(Long exerciseId) {
        // No need to go through the broker, pass it directly
        instanceMessageReceiveService.processScheduleModelingExerciseCancel(exerciseId);
    }

    @Override
    public void sendModelingExerciseInstantClustering(Long exerciseId) {
        // No need to go through the broker, pass it directly
        instanceMessageReceiveService.processModelingExerciseInstantClustering(exerciseId);
    }

    @Override
    public void sendTextExerciseSchedule(Long exerciseId) {
        // No need to go through the broker, pass it directly
        instanceMessageReceiveService.processScheduleTextExercise(exerciseId);
    }

    @Override
    public void sendTextExerciseScheduleCancel(Long exerciseId) {
        // No need to go through the broker, pass it directly
        instanceMessageReceiveService.processTextExerciseScheduleCancel(exerciseId);
    }

    @Override
    public void sendTextExerciseInstantClustering(Long exerciseId) {
        // No need to go through the broker, pass it directly
        instanceMessageReceiveService.processTextExerciseInstantClustering(exerciseId);
    }

    @Override
    public void sendUnlockAllRepositories(Long exerciseId) {
        // No need to go through the broker, pass it directly
        instanceMessageReceiveService.processUnlockAllRepositories(exerciseId);
    }

    @Override
    public void sendLockAllRepositories(Long exerciseId) {
        // No need to go through the broker, pass it directly
        instanceMessageReceiveService.processLockAllRepositories(exerciseId);
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

    @Override
    public void sendExerciseReleaseNotificationSchedule(Long exerciseId) {
        // No need to go through the broker, pass it directly
        instanceMessageReceiveService.processScheduleExerciseReleasedNotification(exerciseId);
    }

    @Override
    public void sendAssessedExerciseSubmissionNotificationSchedule(Long exerciseId) {
        // No need to go through the broker, pass it directly
        instanceMessageReceiveService.processScheduleAssessedExerciseSubmittedNotification(exerciseId);
    }

    @Override
    public void sendExamMonitoringSchedule(Long examId) {
        // No need to go through the broker, pass it directly
        instanceMessageReceiveService.processScheduleExamMonitoring(examId);
    }

    @Override
    public void sendExamMonitoringScheduleCancel(Long examId) {
        // No need to go through the broker, pass it directly
        instanceMessageReceiveService.processScheduleExamMonitoringCancel(examId);
    }
}
