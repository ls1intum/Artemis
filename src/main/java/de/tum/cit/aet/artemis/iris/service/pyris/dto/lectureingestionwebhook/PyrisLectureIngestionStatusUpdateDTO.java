package de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;

/**
 * DTO for lecture ingestion status updates received from Pyris.
 *
 * @param result             human-readable result message
 * @param stages             pipeline stage details
 * @param jobId              identifier of the Pyris job
 * @param errorCode          optional machine-readable error code (e.g. {@code YOUTUBE_PRIVATE});
 *                               serialized as {@code error_code} on the wire.
 *                               Only consumed when the terminal stage is an error;
 *                               ignored (treated as {@code null}) on successful completions
 * @param slidePageNumberMap optional mapping from slide index (0-based) to visible page number in PDF;
 *                               value -1 indicates no page number visible on that slide.
 *                               Serialized as {@code slide_page_number_map} on the wire.
 *                               Only present on successful completion of PDF ingestion.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisLectureIngestionStatusUpdateDTO(String result, List<PyrisStageDTO> stages, long jobId, @Nullable @JsonProperty("error_code") String errorCode,
        @Nullable @JsonProperty("slide_page_number_map") Map<String, Integer> slidePageNumberMap) {
}
