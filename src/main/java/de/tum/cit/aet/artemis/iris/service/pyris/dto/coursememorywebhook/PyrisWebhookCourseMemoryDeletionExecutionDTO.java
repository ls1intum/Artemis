package de.tum.cit.aet.artemis.iris.service.pyris.dto.coursememorywebhook;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionSettingsDTO;

/**
 * Body of a Course Memory deletion webhook request (Artemis &rarr; Pyris,
 * {@code POST /api/v1/webhooks/course-memory/delete}).
 * <p>
 * Covers three scopes, exactly one of which is set — Pyris rejects the request otherwise:
 * <ul>
 * <li>{@code postId} – a thread stopped being memory-worthy: its resolving answer was un-marked or
 * deleted, or the thread itself was removed. Keyed on {@code postId} to match the thread-keyed
 * ingestion entry.</li>
 * <li>{@code conversationId} – a whole channel was deleted or stopped being public, so every entry
 * mined from it has to go. Channel eligibility is only checked when an entry is written, so without
 * this an entry would outlive the channel it came from.</li>
 * <li>{@code wholeCourse} – the course itself was deleted. Its conversations go in one bulk statement,
 * so no channel id survives to purge individually and no Artemis object would be left that could ever
 * ask for these entries' removal.</li>
 * </ul>
 *
 * @param settings       pipeline execution settings (auth token, base url, selection, variant)
 * @param courseId       scopes the deletion; entries are stored per course
 * @param postId         stringified id of the thread's root post whose entry should be removed, or
 *                           {@code null} when deleting a whole channel
 * @param version        monotonic per-thread operation version for the {@code postId} scope, {@code null} for
 *                           the other two. Pyris writes the retraction as a tombstone carrying this version,
 *                           so an ingestion of the thread that was accepted earlier but finishes later finds
 *                           a newer version and cannot resurrect the entry. {@link Long#MAX_VALUE} when the
 *                           thread itself was deleted: its row is gone, so no version can be minted for it,
 *                           and nothing legitimate can ever follow
 * @param conversationId stringified id of the channel whose entries should all be removed, or
 *                           {@code null} when deleting a single thread
 * @param wholeCourse    {@code true} to remove every entry of the course, for the course being deleted.
 *                           An explicit flag rather than "no id given", so a bug that drops an id is
 *                           rejected instead of silently escalating into a course-wide wipe
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisWebhookCourseMemoryDeletionExecutionDTO(PyrisPipelineExecutionSettingsDTO settings, long courseId, @Nullable String postId, @Nullable Long version,
        @Nullable String conversationId, boolean wholeCourse) {

    /**
     * Deletion of a single thread's entry.
     *
     * @param settings pipeline execution settings
     * @param courseId the course the entry is scoped to
     * @param postId   the thread's root post id
     * @param version  the operation version ordering this retraction against the thread's ingestions
     * @return a DTO targeting exactly that thread
     */
    public static PyrisWebhookCourseMemoryDeletionExecutionDTO forThread(PyrisPipelineExecutionSettingsDTO settings, long courseId, String postId, long version) {
        return new PyrisWebhookCourseMemoryDeletionExecutionDTO(settings, courseId, postId, version, null, false);
    }

    /**
     * Deletion of every entry mined from a channel.
     *
     * @param settings       pipeline execution settings
     * @param courseId       the course the entries are scoped to
     * @param conversationId the channel whose entries should be removed
     * @return a DTO targeting the whole channel
     */
    public static PyrisWebhookCourseMemoryDeletionExecutionDTO forConversation(PyrisPipelineExecutionSettingsDTO settings, long courseId, String conversationId) {
        return new PyrisWebhookCourseMemoryDeletionExecutionDTO(settings, courseId, null, null, conversationId, false);
    }

    /**
     * Deletion of every entry of a course, for the course being deleted.
     *
     * @param settings pipeline execution settings
     * @param courseId the course whose entries should all be removed
     * @return a DTO targeting the whole course
     */
    public static PyrisWebhookCourseMemoryDeletionExecutionDTO forCourse(PyrisPipelineExecutionSettingsDTO settings, long courseId) {
        return new PyrisWebhookCourseMemoryDeletionExecutionDTO(settings, courseId, null, null, null, true);
    }
}
