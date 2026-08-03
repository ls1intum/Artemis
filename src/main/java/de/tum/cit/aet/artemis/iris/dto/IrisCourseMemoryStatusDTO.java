package de.tum.cit.aet.artemis.iris.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.CourseMemoryOperation;
import de.tum.cit.aet.artemis.iris.domain.CourseMemoryStage;

/**
 * Progress of a Course Memory run, pushed over the websocket to the user who triggered it on
 * {@code /topic/iris/course-memory/{courseId}}.
 * <p>
 * {@code TRIGGERED} is sent by Artemis at the moment it dispatches the webhook — not when the user
 * clicks — because ingestion is conditional: non-public channels, Iris-disabled courses and
 * bot-authored answers are skipped, and a retraction dispatches a deletion instead. A client-side
 * toast fired on the HTTP response would claim ingestion started in all of those cases.
 * {@code COMPLETED} / {@code FAILED} arrive later, from Pyris' status callback.
 *
 * @param operation    whether the run writes or removes the entry
 * @param stage        how far the run has got
 * @param courseId     the course the entry is scoped to
 * @param postId       stringified id of the thread's root post, the entry's key
 * @param errorMessage failure detail reported by Pyris, only set for {@link CourseMemoryStage#FAILED}
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisCourseMemoryStatusDTO(CourseMemoryOperation operation, CourseMemoryStage stage, long courseId, String postId, @Nullable String errorMessage) {

    public static IrisCourseMemoryStatusDTO triggered(CourseMemoryOperation operation, long courseId, String postId) {
        return new IrisCourseMemoryStatusDTO(operation, CourseMemoryStage.TRIGGERED, courseId, postId, null);
    }

    public static IrisCourseMemoryStatusDTO completed(CourseMemoryOperation operation, long courseId, String postId) {
        return new IrisCourseMemoryStatusDTO(operation, CourseMemoryStage.COMPLETED, courseId, postId, null);
    }

    public static IrisCourseMemoryStatusDTO failed(CourseMemoryOperation operation, long courseId, String postId, @Nullable String errorMessage) {
        return new IrisCourseMemoryStatusDTO(operation, CourseMemoryStage.FAILED, courseId, postId, errorMessage);
    }
}
