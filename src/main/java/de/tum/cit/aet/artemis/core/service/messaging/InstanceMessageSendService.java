package de.tum.cit.aet.artemis.core.service.messaging;

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

    /**
     * Send a message to schedule the unhiding of a slide at its expiration time
     *
     * @param slideId the id of the slide that should be scheduled for unhiding
     */
    void sendSlideUnhideSchedule(Long slideId);

    /**
     * Send a message to cancel the scheduled unhiding of a slide
     *
     * @param slideId the id of the slide whose unhiding task should be cancelled
     */
    void sendSlideUnhideScheduleCancel(Long slideId);
}
