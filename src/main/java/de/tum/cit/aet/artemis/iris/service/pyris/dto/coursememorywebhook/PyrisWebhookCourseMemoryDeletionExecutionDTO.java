package de.tum.cit.aet.artemis.iris.service.pyris.dto.coursememorywebhook;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionSettingsDTO;

/**
 * Body of a Course Memory deletion webhook request (Artemis &rarr; Pyris,
 * {@code POST /api/v1/webhooks/course-memory/delete}).
 * <p>
 * Always one thread: Pyris writes a tombstone carrying {@code version}, so an ingestion of the thread that was
 * accepted earlier but finishes later finds a newer version and cannot resurrect the entry. Deleted channels and
 * courses need no request of their own: Iris only cites from channels Artemis lists as readable when it dispatches
 * a run, and the nightly sync retracts what they left behind.
 *
 * @param settings pipeline execution settings (auth token, base url, selection, variant)
 * @param courseId the course the entry is scoped to
 * @param postId   stringified id of the thread's root post whose entry should be retracted
 * @param version  monotonic per-thread operation version; {@link Long#MAX_VALUE} when the thread itself was deleted,
 *                     since its row is gone and nothing legitimate can ever follow
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisWebhookCourseMemoryDeletionExecutionDTO(PyrisPipelineExecutionSettingsDTO settings, long courseId, String postId, long version) {
}
