package de.tum.cit.aet.artemis.iris.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisLectureSearchResultDTO;

/**
 * DTO pushed to the client via WebSocket for an async Ask Iris response.
 * Sent twice per request: first with plain answer (cited=false), then with citation markers (cited=true).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisSearchWebsocketDTO(boolean cited, String answer, List<PyrisLectureSearchResultDTO> sources) {
}
