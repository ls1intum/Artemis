package de.tum.cit.aet.artemis.lecture.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_EMPTY)
public record LectureUnitSplitInformationDTO(List<LectureUnitSplitDTO> units, int numberOfPages, String removeSlidesCommaSeparatedKeyPhrases) {
}
