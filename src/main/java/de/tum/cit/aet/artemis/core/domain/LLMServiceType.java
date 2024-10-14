package de.tum.cit.aet.artemis.core.domain;

/**
 * Enum representing different types of LLM (Large Language Model) services used in the system.
 */
public enum LLMServiceType {
    /** Athena service for preliminary feedback */
    ATHENA_PRELIMINARY_FEEDBACK,
    /** Athena service for feedback suggestions */
    ATHENA_FEEDBACK_SUGGESTION,
    /** Iris service for code feedback */
    IRIS_CODE_FEEDBACK,
    /** Iris service for course chat messages */
    IRIS_CHAT_COURSE_MESSAGE,
    /** Iris service for exercise chat messages */
    IRIS_CHAT_EXERCISE_MESSAGE,
    /** Iris service for interaction suggestions */
    IRIS_INTERACTION_SUGGESTION,
    /** Iris service for lecture chat messages */
    IRIS_CHAT_LECTURE_MESSAGE,
    /** Iris service for competency generation */
    IRIS_COMPETENCY_GENERATION,
    /** Iris service for citation pipeline */
    IRIS_CITATION_PIPELINE,
    /** Iris service for lecture retrieval pipeline */
    IRIS_LECTURE_RETRIEVAL_PIPELINE,
    /** Iris service for lecture ingestion */
    IRIS_LECTURE_INGESTION,
    /** Default value when the service type is not set */
    NOT_SET
}
