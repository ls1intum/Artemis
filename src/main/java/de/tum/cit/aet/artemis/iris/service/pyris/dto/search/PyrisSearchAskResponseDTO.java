package de.tum.cit.aet.artemis.iris.service.pyris.dto.search;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisSearchAskResponseDTO(String answer, @JsonInclude(JsonInclude.Include.NON_NULL) List<PyrisLectureSearchResultDTO> sources) {
}
