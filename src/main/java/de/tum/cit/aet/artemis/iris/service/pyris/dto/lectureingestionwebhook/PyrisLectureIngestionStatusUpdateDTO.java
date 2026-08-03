package de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisActivityDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStatusErrorDTO;

/**
 * DTO for lecture ingestion status updates received from Pyris.
 *
 * @param result             result payload from Pyris
 * @param runState           current pipeline run state
 * @param error              optional error details; {@code error.code} carries ingestion error codes
 * @param jobId              identifier of the Pyris job
 * @param displayPageNumbers optional slide-to-displayed-page-number mapping from Pyris;
 *                               only expected on terminal callbacks and {@code null} otherwise
 * @param activities         optional live per-step progress snapshot (named pipeline steps with state and duration),
 *                               used by the admin ingestion dashboard; null on callbacks that carry no progress
 * @param activitySeq        optional monotonically increasing sequence number so out-of-order snapshots can be dropped
 * @param startedAt          optional ISO-8601 run start time, so the dashboard can show elapsed time
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisLectureIngestionStatusUpdateDTO(String result, PyrisRunState runState, @Nullable PyrisStatusErrorDTO error, long jobId,
        @Nullable List<Integer> displayPageNumbers, @Nullable List<PyrisActivityDTO> activities, @Nullable Integer activitySeq, @Nullable String startedAt) {

    /**
     * Backwards-compatible constructor for callers that predate the live-progress fields.
     *
     * @param result             result payload from Pyris
     * @param runState           current pipeline run state
     * @param error              optional error details
     * @param jobId              identifier of the Pyris job
     * @param displayPageNumbers optional slide-to-displayed-page-number mapping
     */
    public PyrisLectureIngestionStatusUpdateDTO(String result, PyrisRunState runState, @Nullable PyrisStatusErrorDTO error, long jobId,
            @Nullable List<Integer> displayPageNumbers) {
        this(result, runState, error, jobId, displayPageNumbers, null, null, null);
    }
}
