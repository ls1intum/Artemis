package de.tum.cit.aet.artemis.atlas.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.RelationType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UpdateCourseCompetencyRelationDTO(RelationType newRelationType) {
}
