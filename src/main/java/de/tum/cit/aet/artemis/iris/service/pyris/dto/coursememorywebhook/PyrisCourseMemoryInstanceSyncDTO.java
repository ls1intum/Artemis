package de.tum.cit.aet.artemis.iris.service.pyris.dto.coursememorywebhook;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionSettingsDTO;

/**
 * Body of the nightly Course Memory sync of the whole Artemis instance (Artemis &rarr; Pyris,
 * {@code POST /api/v1/webhooks/course-memory/sync/instance}). Pyris retracts the entries of every course not in
 * {@code courseIds} for good, and the entries of every existing course not in {@code courseIdsWithThreads}, which
 * receives no course sync.
 * <p>
 * Both lists use a bare {@code @JsonInclude()} so that an empty list is still sent: Pyris requires both, because a
 * list that went missing must never read as "no courses".
 *
 * @param settings             pipeline execution settings (auth token, base url)
 * @param snapshotAt           ISO-8601 instant at which Artemis read the course lists
 * @param courseIds            the ids of all courses that exist
 * @param courseIdsWithThreads the ids of the courses with at least one thread that has a Course Memory version
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisCourseMemoryInstanceSyncDTO(PyrisPipelineExecutionSettingsDTO settings, String snapshotAt, @JsonInclude() List<Long> courseIds,
        @JsonInclude() List<Long> courseIdsWithThreads) {
}
