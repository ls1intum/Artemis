package de.tum.cit.aet.artemis.globalsearch.domain;

/**
 * The kind of Weaviate write a {@link WeaviateOutboxEntry} represents.
 * <p>
 * {@link #UPSERT} and {@link #DELETE_ENTITY} operate on a single {@code (entity_type, entity_id)} row.
 * The {@code DELETE_*} bulk variants operate on a filter and carry their parameters as JSON in
 * {@link WeaviateOutboxEntry#getParams()}. Each value maps to exactly one internal write method that the
 * dispatcher invokes on {@code SearchableEntityWeaviateService}.
 */
public enum WeaviateOutboxOperation {

    /**
     * Upsert a single row from a stored payload snapshot ({@code entity_type}, {@code entity_id}, {@code payload}).
     */
    UPSERT,

    /**
     * Delete a single row ({@code entity_type}, {@code entity_id}).
     */
    DELETE_ENTITY,

    /**
     * Delete all POST and ANSWER_POST rows for a channel ({@code params.channelId}).
     */
    DELETE_POSTS_FOR_CHANNEL,

    /**
     * Delete all POST and ANSWER_POST rows for a course ({@code params.courseId}).
     */
    DELETE_POSTS_FOR_COURSE,

    /**
     * Delete all ANSWER_POST rows for a post ({@code params.postId}).
     */
    DELETE_ANSWER_POSTS_FOR_POST,

    /**
     * Delete all rows for a course, regardless of type ({@code params.courseId}).
     */
    DELETE_ALL_FOR_COURSE,

    /**
     * Delete all LECTURE_UNIT rows for a lecture ({@code params.lectureId}).
     */
    DELETE_LECTURE_UNITS_FOR_LECTURE
}
