package de.tum.cit.aet.artemis.iris.service.pyris.dto.transcription;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;

/**
 * DTO for receiving transcription status updates from Pyris.
 * Contains progress stages and the final result when transcription is complete.
 *
 * @param stages        List of transcription stages with their current state
 * @param result        JSON string containing the transcription result (null until complete)
 * @param lectureUnitId The lecture unit ID being transcribed
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisTranscriptionStatusUpdateDTO(List<PyrisStageDTO> stages, String result, Long lectureUnitId) {
}
