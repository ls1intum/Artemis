package de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

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
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisLectureIngestionStatusUpdateDTO(String result, PyrisRunState runState, @Nullable PyrisStatusErrorDTO error, long jobId,
        @Nullable List<Integer> displayPageNumbers) {
}
