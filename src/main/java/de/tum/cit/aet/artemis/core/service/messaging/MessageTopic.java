package de.tum.cit.aet.artemis.core.service.messaging;

/**
 * Topic identifiers for Hazelcast messages between instances.
 */
public enum MessageTopic {

    // @formatter:off
    PROGRAMMING_EXERCISE_SCHEDULE("programming-exercise-schedule"),
    PROGRAMMING_EXERCISE_SCHEDULE_CANCEL("programming-exercise-schedule-cancel"),
    MODELING_EXERCISE_SCHEDULE("modeling-exercise-schedule"),
    MODELING_EXERCISE_SCHEDULE_CANCEL("modeling-exercise-schedule-cancel"),
    MODELING_EXERCISE_INSTANT_CLUSTERING("modeling-exercise-instant-clustering"),
    TEXT_EXERCISE_SCHEDULE("text-exercise-schedule"),
    TEXT_EXERCISE_SCHEDULE_CANCEL("text-exercise-schedule-cancel"),
    PROGRAMMING_EXERCISE_UNLOCK_REPOSITORIES("programming-exercise-unlock-repositories"),
    PROGRAMMING_EXERCISE_UNLOCK_REPOSITORIES_AND_PARTICIPATIONS("programming-exercise-unlock-repositories-and-participations"),
    PROGRAMMING_EXERCISE_UNLOCK_REPOSITORIES_AND_PARTICIPATIONS_WITH_EARLIER_START_DATE_AND_LATER_DUE_DATE("programming-exercise-unlock-repositories-and-participations-with-earlier-start-date-and-later-due-date"),
    PROGRAMMING_EXERCISE_UNLOCK_REPOSITORIES_WITH_EARLIER_START_DATE_AND_LATER_DUE_DATE("programming-exercise-unlock-repositories-with-earlier-start-date-and-later-due-date"),
    PROGRAMMING_EXERCISE_UNLOCK_PARTICIPATIONS_WITH_EARLIER_START_DATE_AND_LATER_DUE_DATE("programming-exercise-unlock-participations-with-earlier-start-date-and-later-due-date"),
    PROGRAMMING_EXERCISE_LOCK_REPOSITORIES_AND_PARTICIPATIONS("programming-exercise-lock-repositories-and-participations"),
    PROGRAMMING_EXERCISE_LOCK_REPOSITORIES("programming-exercise-lock-repositories"),
    PROGRAMMING_EXERCISE_LOCK_REPOSITORIES_AND_PARTICIPATIONS_WITH_EARLIER_DUE_DATE("programming-exercise-lock-repositories-and-participations-with-earlier-due-date"),
    PROGRAMMING_EXERCISE_LOCK_PARTICIPATIONS_WITH_EARLIER_DUE_DATE("programming-exercise-lock-participations-with-earlier-due-date"),
    USER_MANAGEMENT_REMOVE_NON_ACTIVATED_USERS("user-management-remove-non-activated-users"),
    USER_MANAGEMENT_CANCEL_REMOVE_NON_ACTIVATED_USERS("user-management-cancel-remove-non-activated-users"),
    EXERCISE_RELEASED_SCHEDULE("exercise-released-schedule"),
    ASSESSED_EXERCISE_SUBMISSION_SCHEDULE("assessed-exercise-submission-schedule"),
    EXAM_RESCHEDULE_DURING_CONDUCTION("exam-reschedule-during-conduction"),
    STUDENT_EXAM_RESCHEDULE_DURING_CONDUCTION("student-exam-reschedule-during-conduction"),
    PARTICIPANT_SCORE_SCHEDULE("participant-score-schedule"),
    QUIZ_EXERCISE_START_SCHEDULE("quiz-exercise-start-schedule"),
    QUIZ_EXERCISE_START_CANCEL("quiz-exercise-start-cancel");
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
