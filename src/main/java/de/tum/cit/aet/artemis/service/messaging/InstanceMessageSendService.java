package de.tum.cit.aet.artemis.service.messaging;

/**
 * This interface offers a service that will send messages to the node that runs the scheduling.
 * This can either be the same node or a different node within the Hazelcast cluster.
 */
public interface InstanceMessageSendService {

    /**
     * Send a message to the main server that a programming exercise was created or updated and a (re-)scheduling has to be performed
     *
     * @param exerciseId the id of the exercise that should be scheduled
     */
    void sendProgrammingExerciseSchedule(Long exerciseId);

    /**
     * Send a message to the main server that a programming exercise was deleted and the scheduling should be cancelled
     *
     * @param exerciseId the id of the exercise that should be no longer be scheduled
     */
    void sendProgrammingExerciseScheduleCancel(Long exerciseId);

    /**
     * Send a message to the main server that a modeling exercise was created or updated and a (re-)scheduling has to be performed
     *
     * @param exerciseId the id of the exercise that should be scheduled
     */
    void sendModelingExerciseSchedule(Long exerciseId);

    /**
     * Send a message to the main server that a modeling exercise was deleted and the scheduling should be cancelled
     *
     * @param exerciseId the id of the exercise that should be no longer be scheduled
     */
    void sendModelingExerciseScheduleCancel(Long exerciseId);

    /**
     * Send a message to the main server that a modeling exercise should be instantly get clustered
     *
     * @param exerciseId the id of the exercise that should be clustered
     */
    void sendModelingExerciseInstantClustering(Long exerciseId);

    /**
     * Send a message to the main server that a text exercise was created or updated and a (re-)scheduling has to be performed
     *
     * @param exerciseId the id of the exercise that should be scheduled
     */
    void sendTextExerciseSchedule(Long exerciseId);

    /**
     * Send a message to the main server that a text exercise was deleted and a scheduling should be stopped
     *
     * @param exerciseId the id of the exercise that should no longer be scheduled
     */
    void sendTextExerciseScheduleCancel(Long exerciseId);

    /**
     * Send a message to the main server that all student repositories and student participations of an exercise should be instantly locked
     *
     * @param exerciseId the id of the exercise that should be locked
     */
    void sendLockAllStudentRepositoriesAndParticipations(Long exerciseId);

    /**
     * Send a message to the main server that all student repositories of an exercise should be instantly locked.
     * This does not lock the participations associated with the repositories! See {@link #sendLockAllStudentRepositoriesAndParticipations(Long)} for that.
     *
     * @param exerciseId the id of the exercise that should be locked
     */
    void sendLockAllStudentRepositories(Long exerciseId);

    /**
     * Send a message to the main server that all student repositories and student participations, whose due date is in the past, should be instantly locked.
     *
     * @param exerciseId the id of the exercise that should be locked
     */
    void sendLockAllStudentRepositoriesAndParticipationsWithEarlierDueDate(Long exerciseId);

    /**
     * Send a message to the main server that all student participations, whose due date is in the past, should be instantly locked.
     * This does not lock the repositories associated with the participations! See {@link #sendLockAllStudentRepositoriesAndParticipationsWithEarlierDueDate(Long)} for that.
     *
     * @param exerciseId the id of the exercise that should be locked
     */
    void sendLockAllStudentParticipationsWithEarlierDueDate(Long exerciseId);

    /**
     * Send a message to the main server that all student repositories of an exercise should be instantly unlocked.
     * This does not unlock the participations associated with the repositories! See {@link #sendUnlockAllStudentRepositoriesAndParticipations(Long)} for that.
     *
     * @param exerciseId the id of the exercise that should be unlocked
     */
    void sendUnlockAllStudentRepositories(Long exerciseId);

    /**
     * Send a message to the main server that all student repositories and student participations of an exercise should be instantly unlocked.
     *
     * @param exerciseId the id of the exercise that should be unlocked
     */
    void sendUnlockAllStudentRepositoriesAndParticipations(Long exerciseId);

    /**
     * Send a message to the main server that all student repositories and student participations, whose start date is before now and whose due date is after now, should be
     * instantly unlocked.
     * Submissions are allowed if the start date is in the past and the due date is in the future.
     *
     * @param exerciseId the id of the exercise that should be unlocked
     */
    void sendUnlockAllStudentRepositoriesAndParticipationsWithEarlierStartDateAndLaterDueDate(Long exerciseId);

    /**
     * Send a message to the main server that all student repositories, whose start date is before now and whose due date is after now, should be instantly unlocked.
     * This does not unlock the participations associated with the repositories! See
     * {@link #sendUnlockAllStudentRepositoriesAndParticipationsWithEarlierStartDateAndLaterDueDate(Long)} for that.
     *
     * @param exerciseId the id of the exercise that should be unlocked
     */
    void sendUnlockAllStudentRepositoriesWithEarlierStartDateAndLaterDueDate(Long exerciseId);

    /**
     * Send a message to the main server that all student participations, whose start date is before now and whose due date is after now, should be instantly unlocked.
     * This does not unlock the repositories associated with the participations! See
     * {@link #sendUnlockAllStudentRepositoriesAndParticipationsWithEarlierStartDateAndLaterDueDate(Long)} for that.
     *
     * @param exerciseId the id of the exercise that should be unlocked
     */
    void sendUnlockAllStudentParticipationsWithEarlierStartDateAndLaterDueDate(Long exerciseId);

    /**
     * Send a message to the main server that the non-activated should be deleted.
     *
     * @param userId the user id of the non-activated user
     */
    void sendRemoveNonActivatedUserSchedule(Long userId);

    /**
     * Send a message to the main server that cancels the schedule to remove the non-activated user.
     *
     * @param userId the user id of the non-activated user
     */
    void sendCancelRemoveNonActivatedUserSchedule(Long userId);

    /**
     * Send a message to the main server that schedules a notification for a released exercise
     *
     * @param exerciseId of the exercise a notification should be created for
     */
    void sendExerciseReleaseNotificationSchedule(Long exerciseId);

    /**
     * Send a message to the main server that schedules a notification for an assessed exercise submission
     *
     * @param exerciseId of the exercise a notification should be created for
     */
    void sendAssessedExerciseSubmissionNotificationSchedule(Long exerciseId);

    /**
     * Send a message to the main server that all student exams of an exam should get rescheduled
     * e.g. because the working time of an exam was changed during the conduction.
     *
     * @param examId the id of the exam that should be scheduled
     */
    void sendRescheduleAllStudentExams(Long examId);

    /**
     * Send a message to the main server that the working time of a student exam was changed during the conduction and rescheduling might be necessary
     *
     * @param studentExamId the id of the student exam that should be scheduled
     */
    void sendStudentExamIndividualWorkingTimeChangeDuringConduction(Long studentExamId);

    /**
     * Send a message to the main server that schedules to update the participant score for this exercise/participant
     *
     * @param exerciseId          the id of the exercise
     * @param participantId       the id of the participant
     * @param resultIdToBeDeleted the id of the result about to be deleted
     */
    void sendParticipantScoreSchedule(Long exerciseId, Long participantId, Long resultIdToBeDeleted);

    /**
     * Send a message to the main server that a quiz exercise was created or updated and a (re-)scheduling has to be performed
     *
     * @param quizExerciseId the id of the quiz exercise that should be scheduled
     */
    void sendQuizExerciseStartSchedule(Long quizExerciseId);

    /**
     * Send a message to the main server that a quiz exercise was deleted and the scheduling should be cancelled
     *
     * @param quizExerciseId the id of the quiz exercise that should be no longer be scheduled
     */
    void sendQuizExerciseStartCancel(Long quizExerciseId);
}
