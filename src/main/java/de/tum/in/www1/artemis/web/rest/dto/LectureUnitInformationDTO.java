package de.tum.in.www1.artemis.web.rest.dto;

import java.util.List;

public record LectureUnitInformationDTO(List<LectureUnitSplitDTO> lectureUnitDTOS, Integer numberOfPages) {
}
