package de.tum.cit.aet.artemis.service.messaging;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_SCHEDULING;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * This service is only present on a node that runs the 'scheduling' profile.
 * As this node can handle all the processing without interaction with another node, everything is handled locally (without Hazelcast).
 * Important: There is no need to go through the broker, as this class is only active on the main instance!
 */
@Service
@Profile(PROFILE_SCHEDULING)
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
        instanceMessageReceiveService.processSchedulePotentialAthenaExercise(exerciseId);
    }

    @Override
    public void sendTextExerciseScheduleCancel(Long exerciseId) {
        instanceMessageReceiveService.processPotentialAthenaExerciseScheduleCancel(exerciseId);
    }

    @Override
    public void sendUnlockAllStudentRepositories(Long exerciseId) {
        instanceMessageReceiveService.processUnlockAllRepositories(exerciseId);
    }

    @Override
    public void sendUnlockAllStudentRepositoriesAndParticipations(Long exerciseId) {
        instanceMessageReceiveService.processUnlockAllRepositoriesAndParticipations(exerciseId);
    }

    @Override
    public void sendUnlockAllStudentRepositoriesAndParticipationsWithEarlierStartDateAndLaterDueDate(Long exerciseId) {
        instanceMessageReceiveService.processUnlockAllRepositoriesAndParticipationsWithEarlierStartDateAndLaterDueDate(exerciseId);
    }

    @Override
    public void sendUnlockAllStudentRepositoriesWithEarlierStartDateAndLaterDueDate(Long exerciseId) {
        instanceMessageReceiveService.processUnlockAllRepositoriesWithEarlierStartDateAndLaterDueDate(exerciseId);
    }

    @Override
    public void sendUnlockAllStudentParticipationsWithEarlierStartDateAndLaterDueDate(Long exerciseId) {
        instanceMessageReceiveService.processUnlockAllParticipationsWithEarlierStartDateAndLaterDueDate(exerciseId);
    }

    @Override
    public void sendLockAllStudentRepositoriesAndParticipations(Long exerciseId) {
        instanceMessageReceiveService.processLockAllRepositoriesAndParticipations(exerciseId);
    }

    @Override
    public void sendLockAllStudentRepositories(Long exerciseId) {
        instanceMessageReceiveService.processLockAllRepositories(exerciseId);
    }

    @Override
    public void sendLockAllStudentRepositoriesAndParticipationsWithEarlierDueDate(Long exerciseId) {
        instanceMessageReceiveService.processLockAllRepositoriesAndParticipationsWithEarlierDueDate(exerciseId);
    }

    @Override
    public void sendLockAllStudentParticipationsWithEarlierDueDate(Long exerciseId) {
        instanceMessageReceiveService.processLockAllParticipationsWithEarlierDueDate(exerciseId);
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
    public void sendRescheduleAllStudentExams(Long examId) {
        instanceMessageReceiveService.processRescheduleExamDuringConduction(examId);
    }

    @Override
    public void sendStudentExamIndividualWorkingTimeChangeDuringConduction(Long studentExamId) {
        instanceMessageReceiveService.processStudentExamIndividualWorkingTimeChangeDuringConduction(studentExamId);
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
}
