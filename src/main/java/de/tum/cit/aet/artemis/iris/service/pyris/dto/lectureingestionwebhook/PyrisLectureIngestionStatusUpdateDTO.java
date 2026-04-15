package de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;

/**
 * DTO for lecture ingestion status updates received from Pyris.
 *
 * @param result    human-readable result message
 * @param stages    pipeline stage details
 * @param jobId     identifier of the Pyris job
 * @param errorCode optional machine-readable error code (e.g. {@code YOUTUBE_PRIVATE});
 *                      serialized as {@code error_code} on the wire
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisLectureIngestionStatusUpdateDTO(String result, List<PyrisStageDTO> stages, long jobId, @Nullable @JsonProperty("error_code") String errorCode) {
}
