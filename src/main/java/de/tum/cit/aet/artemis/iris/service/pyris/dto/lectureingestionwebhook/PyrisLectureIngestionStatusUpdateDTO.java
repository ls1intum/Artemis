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
 * @param result    result payload from Pyris
 * @param stages    pipeline stage details
 * @param jobId     identifier of the Pyris job
 * @param errorCode optional machine-readable error code (e.g. {@code YOUTUBE_PRIVATE});
 *                      serialized as {@code error_code} on the wire.
 *                      Only consumed when the terminal stage is an error;
 *                      ignored (treated as {@code null}) on successful completions
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisLectureIngestionStatusUpdateDTO(String result, List<PyrisStageDTO> stages, @JsonAlias("id") long jobId, @Nullable @JsonProperty("error_code") String errorCode) {
}
