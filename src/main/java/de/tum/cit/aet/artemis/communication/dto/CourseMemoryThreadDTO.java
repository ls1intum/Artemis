package de.tum.cit.aet.artemis.communication.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A thread that may have a Course Memory entry: its root post, where it lives, and its current Course Memory version.
 *
 * @param postId         the thread's root post id
 * @param conversationId the channel the thread lives in
 * @param courseId       the course of that channel
 * @param version        the thread's current Course Memory version
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseMemoryThreadDTO(long postId, long conversationId, long courseId, long version) {
}
