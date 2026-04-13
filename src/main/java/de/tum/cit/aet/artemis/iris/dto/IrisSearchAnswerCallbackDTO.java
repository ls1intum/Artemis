package de.tum.cit.aet.artemis.iris.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisLectureSearchResultDTO;

/**
 * DTO received from Pyris as a callback for an async Ask Iris request.
 * Pyris sends this twice: once with the plain answer (cited=false) and once with inline citation markers (cited=true).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisSearchAnswerCallbackDTO(boolean cited, String answer, List<PyrisLectureSearchResultDTO> sources) {
}
