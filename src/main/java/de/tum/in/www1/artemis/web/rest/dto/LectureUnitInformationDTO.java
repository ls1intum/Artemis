package de.tum.in.www1.artemis.web.rest.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public record LectureUnitInformationDTO(List<LectureUnitSplitDTO> units, int numberOfPages, String removeSlidesCommaSeparatedKeyPhrases) {
}
