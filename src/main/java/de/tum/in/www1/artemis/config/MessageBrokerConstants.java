package de.tum.in.www1.artemis.config;

/**
 * Message broker constants containing names of the used queues for communication between microservices.
 */
public final class MessageBrokerConstants {

    // Artemis <-> Lecture Microservice queues
    public static final String LECTURE_QUEUE_GET_EXERCISES = "lecture_queue.get_lecture_exercises";

    public static final String LECTURE_QUEUE_GET_EXERCISES_RESPONSE = "lecture_queue.get_lecture_exercises_response";

    public static final String LECTURE_QUEUE_FILTER_ACTIVE_ATTACHMENTS = "lecture_queue.filter_active_attachments";

    public static final String LECTURE_QUEUE_FILTER_ACTIVE_ATTACHMENTS_RESPONSE = "lecture_queue.filter_active_attachments_response";

    public static final String LECTURE_QUEUE_REMOVE_EXERCISE_UNITS = "lecture_queue.remove_exercise_units";

    public static final String LECTURE_QUEUE_REMOVE_EXERCISE_UNITS_RESPONSE = "lecture_queue.remove_exercise_units_response";

    public static final String LECTURE_QUEUE_DELETE_LECTURES = "lecture_queue.delete_lectures";

    public static final String LECTURE_QUEUE_DELETE_LECTURES_RESPONSE = "lecture_queue.delete_lectures_response";

    private MessageBrokerConstants() {
    }
}
