package de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;

/**
 * DTO for lecture ingestion status updates received from Pyris.
 *
 * @param result    result payload from Pyris; also accepts {@code final_result}
 * @param stages    pipeline stage details
 * @param jobId     identifier of the Pyris job; also accepts {@code id}
 * @param errorCode optional machine-readable error code serialized as {@code error_code}
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisLectureIngestionStatusUpdateDTO(@JsonAlias("final_result") String result, List<PyrisStageDTO> stages, @JsonAlias("id") long jobId,
        @Nullable @JsonProperty("error_code") String errorCode) {
}
