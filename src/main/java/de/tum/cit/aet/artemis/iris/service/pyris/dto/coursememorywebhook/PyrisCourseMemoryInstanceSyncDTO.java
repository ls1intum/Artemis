package de.tum.cit.aet.artemis.iris.service.pyris.dto.coursememorywebhook;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionSettingsDTO;

/**
 * Body of the nightly Course Memory sync of the whole Artemis instance (Artemis &rarr; Pyris,
 * {@code POST /api/v1/webhooks/course-memory/sync/instance}). Pyris retracts the entries of every course not listed.
 *
 * @param settings   pipeline execution settings (auth token, base url)
 * @param snapshotAt ISO-8601 instant at which Artemis read the course list
 * @param courseIds  the ids of all courses that exist
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisCourseMemoryInstanceSyncDTO(PyrisPipelineExecutionSettingsDTO settings, String snapshotAt, List<Long> courseIds) {
}
