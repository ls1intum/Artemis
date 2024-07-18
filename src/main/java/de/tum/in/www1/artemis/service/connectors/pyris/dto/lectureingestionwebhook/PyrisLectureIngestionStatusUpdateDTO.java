package de.tum.in.www1.artemis.service.connectors.pyris.dto.lectureingestionwebhook;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisLectureIngestionStatusUpdateDTO(Optional<Long> id, String result, List<PyrisStageDTO> stages) {
}
