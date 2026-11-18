package de.tum.cit.aet.artemis.atlas.dto.metrics;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyLectureUnitMasteryCalculationDTO(long lectureUnitId, boolean completed, double competencyLinkWeight) {
}
