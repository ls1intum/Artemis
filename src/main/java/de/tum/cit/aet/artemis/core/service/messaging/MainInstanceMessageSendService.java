package de.tum.cit.aet.artemis.core.service.messaging;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE_AND_SCHEDULING;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * This service is only present on a node that runs the 'scheduling' profile.
 * As this node can handle all the processing without interaction with another node, everything is handled locally (without Hazelcast).
 * Important: There is no need to go through the broker, as this class is only active on the main instance!
 */
@Lazy
@Service
@Profile(PROFILE_CORE_AND_SCHEDULING)
public class MainInstanceMessageSendService implements InstanceMessageSendService {

    public final InstanceMessageReceiveService instanceMessageReceiveService;

    public MainInstanceMessageSendService(InstanceMessageReceiveService instanceMessageReceiveService) {
        this.instanceMessageReceiveService = instanceMessageReceiveService;
    }

    @Override
    public void sendProgrammingExerciseSchedule(Long exerciseId) {
        instanceMessageReceiveService.processScheduleProgrammingExercise(exerciseId);
        instanceMessageReceiveService.processSchedulePotentialAthenaExercise(exerciseId);
    }

    @Override
    public void sendProgrammingExerciseScheduleCancel(Long exerciseId) {
        instanceMessageReceiveService.processScheduleProgrammingExerciseCancel(exerciseId);
        instanceMessageReceiveService.processPotentialAthenaExerciseScheduleCancel(exerciseId);
    }

    @Override
    public void sendTextExerciseSchedule(Long exerciseId) {
        instanceMessageReceiveService.processSchedulePotentialAthenaExercise(exerciseId);
    }

    @Override
    public void sendTextExerciseScheduleCancel(Long exerciseId) {
        instanceMessageReceiveService.processPotentialAthenaExerciseScheduleCancel(exerciseId);
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
    public void sendParticipantScoreSchedule(Long exerciseId, Long participantId, Long resultIdToBeDeleted) {
        instanceMessageReceiveService.processScheduleParticipantScore(exerciseId, participantId, resultIdToBeDeleted);
    }

    @Override
    public void sendQuizExerciseStartSchedule(Long exerciseId) {
        instanceMessageReceiveService.processScheduleQuizStart(exerciseId);
    }

    @Override
    public void sendQuizExerciseStartCancel(Long exerciseId) {
        instanceMessageReceiveService.processCancelQuizStart(exerciseId);
    }

    @Override
    public void sendSlideUnhideSchedule(Long slideId) {
        instanceMessageReceiveService.processScheduleSlideUnhide(slideId);
    }

    @Override
    public void sendSlideUnhideScheduleCancel(Long slideId) {
        instanceMessageReceiveService.processCancelSlideUnhide(slideId);
    }
}
