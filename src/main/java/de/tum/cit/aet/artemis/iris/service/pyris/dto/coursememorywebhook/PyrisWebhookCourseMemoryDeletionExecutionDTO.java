package de.tum.cit.aet.artemis.iris.service.pyris.dto.coursememorywebhook;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionSettingsDTO;

/**
 * Body of a Course Memory deletion webhook request (Artemis &rarr; Pyris,
 * {@code POST /api/v1/webhooks/course-memory/delete}).
 * <p>
 * Sent when a thread stops being memory-worthy: its resolving answer was un-marked or deleted, or
 * the thread itself was removed. Keyed on {@code postId} to match the thread-keyed ingestion entry.
 *
 * @param settings pipeline execution settings (auth token, base url, selection, variant)
 * @param courseId scopes the deletion; entries are stored per course
 * @param postId   stringified id of the thread's root post whose entry should be removed
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisWebhookCourseMemoryDeletionExecutionDTO(PyrisPipelineExecutionSettingsDTO settings, long courseId, String postId) {
}
