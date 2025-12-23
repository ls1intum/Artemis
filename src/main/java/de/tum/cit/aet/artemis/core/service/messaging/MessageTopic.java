package de.tum.cit.aet.artemis.core.service.messaging;

/**
 * Topic identifiers for Hazelcast messages between instances.
 */
public enum MessageTopic {

    // @formatter:off
    PROGRAMMING_EXERCISE_SCHEDULE("programming-exercise-schedule"),
    PROGRAMMING_EXERCISE_SCHEDULE_CANCEL("programming-exercise-schedule-cancel"),
    TEXT_EXERCISE_SCHEDULE("text-exercise-schedule"),
    TEXT_EXERCISE_SCHEDULE_CANCEL("text-exercise-schedule-cancel"),
    USER_MANAGEMENT_REMOVE_NON_ACTIVATED_USERS("user-management-remove-non-activated-users"),
    USER_MANAGEMENT_CANCEL_REMOVE_NON_ACTIVATED_USERS("user-management-cancel-remove-non-activated-users"),
    EXERCISE_RELEASED_SCHEDULE("exercise-released-schedule"),
    ASSESSED_EXERCISE_SUBMISSION_SCHEDULE("assessed-exercise-submission-schedule"),
    EXAM_RESCHEDULE_DURING_CONDUCTION("exam-reschedule-during-conduction"),
    STUDENT_EXAM_RESCHEDULE_DURING_CONDUCTION("student-exam-reschedule-during-conduction"),
    PARTICIPANT_SCORE_SCHEDULE("participant-score-schedule"),
    QUIZ_EXERCISE_START_SCHEDULE("quiz-exercise-start-schedule"),
    QUIZ_EXERCISE_START_CANCEL("quiz-exercise-start-cancel"),
    SLIDE_UNHIDE_SCHEDULE("slide-unhide-schedule"),
    SLIDE_UNHIDE_SCHEDULE_CANCEL("slide-unhide-schedule-cancel"),
    WEBSOCKET_BROKER_RECONNECT("websocket-broker-reconnect");
    // @formatter:on

    private final String topic;

    MessageTopic(String topic) {
        this.topic = topic;
    }

    @Override
    public String toString() {
        return topic;
    }
}
