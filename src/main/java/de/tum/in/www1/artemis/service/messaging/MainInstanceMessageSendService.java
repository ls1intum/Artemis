package de.tum.in.www1.artemis.service.messaging;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggle;

/**
 * This service is only present on a node that runs the 'scheduling' profile.
 * As this node can handle all the processing without interaction with another node, everything is handled locally (without Hazelcast).
 * Important: There is no need to go through the broker, as this class is only active on the main instance!
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
        instanceMessageReceiveService.processScheduleProgrammingExercise(exerciseId);
    }

    @Override
    public void sendProgrammingExerciseScheduleCancel(Long exerciseId) {
        instanceMessageReceiveService.processScheduleProgrammingExerciseCancel(exerciseId);
    }

    @Override
    public void sendModelingExerciseSchedule(Long exerciseId) {
        instanceMessageReceiveService.processScheduleModelingExercise(exerciseId);
    }

    @Override
    public void sendModelingExerciseScheduleCancel(Long exerciseId) {
        instanceMessageReceiveService.processScheduleModelingExerciseCancel(exerciseId);
    }

    @Override
    public void sendModelingExerciseInstantClustering(Long exerciseId) {
        instanceMessageReceiveService.processModelingExerciseInstantClustering(exerciseId);
    }

    @Override
    public void sendTextExerciseSchedule(Long exerciseId) {
        instanceMessageReceiveService.processScheduleTextExercise(exerciseId);
    }

    @Override
    public void sendTextExerciseScheduleCancel(Long exerciseId) {
        instanceMessageReceiveService.processTextExerciseScheduleCancel(exerciseId);
    }

    @Override
    public void sendTextExerciseInstantClustering(Long exerciseId) {
        instanceMessageReceiveService.processTextExerciseInstantClustering(exerciseId);
    }

    @Override
    public void sendUnlockAllRepositories(Long exerciseId) {
        instanceMessageReceiveService.processUnlockAllRepositories(exerciseId);
    }

    @Override
    public void sendLockAllRepositories(Long exerciseId) {
        instanceMessageReceiveService.processLockAllRepositories(exerciseId);
    }

    @Override
    public void sendRemoveNonActivatedUserSchedule(Long userId) {
        instanceMessageReceiveService.processRemoveNonActivatedUser(userId);
    }

    @Override
    public void sendCancelRemoveNonActivatedUserSchedule(Long userId) {
        instanceMessageReceiveService.processCancelRemoveNonActivatedUser(userId);
    }

    @Override
    public void sendExerciseReleaseNotificationSchedule(Long exerciseId) {
        instanceMessageReceiveService.processScheduleExerciseReleasedNotification(exerciseId);
    }

    @Override
    public void sendAssessedExerciseSubmissionNotificationSchedule(Long exerciseId) {
        instanceMessageReceiveService.processScheduleAssessedExerciseSubmittedNotification(exerciseId);
    }

    @Override
    @FeatureToggle(Feature.ExamLiveStatistics)
    public void sendExamMonitoringSchedule(Long examId) {
        instanceMessageReceiveService.processScheduleExamMonitoring(examId);
    }

    @Override
    public void sendExamMonitoringScheduleCancel(Long examId) {
        instanceMessageReceiveService.processScheduleExamMonitoringCancel(examId);
    }

    @Override
    public void sendResultSchedule(Long participationId) {
        instanceMessageReceiveService.processScheduleResult(participationId);
    }
}
