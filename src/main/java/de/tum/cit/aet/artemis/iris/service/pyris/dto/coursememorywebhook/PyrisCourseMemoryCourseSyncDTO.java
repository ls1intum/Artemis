package de.tum.cit.aet.artemis.iris.service.pyris.dto.coursememorywebhook;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionSettingsDTO;

/**
 * Body of the nightly Course Memory sync of one course (Artemis &rarr; Pyris,
 * {@code POST /api/v1/webhooks/course-memory/sync/course}).
 * <p>
 * The list must be complete for the course: Pyris retracts every entry whose thread is not listed.
 *
 * @param settings   pipeline execution settings (auth token, base url)
 * @param snapshotAt ISO-8601 instant at which Artemis read the state it reports
 * @param courseId   the course
 * @param threads    every thread of the course with a Course Memory version
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisCourseMemoryCourseSyncDTO(PyrisPipelineExecutionSettingsDTO settings, String snapshotAt, long courseId, List<PyrisCourseMemorySyncThreadDTO> threads) {
}
