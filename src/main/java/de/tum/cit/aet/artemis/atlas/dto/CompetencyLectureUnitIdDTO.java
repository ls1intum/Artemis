package de.tum.cit.aet.artemis.atlas.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO carrying one link between a competency and a lecture unit by their ids.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyLectureUnitIdDTO(long competencyId, long lectureUnitId) {
}
