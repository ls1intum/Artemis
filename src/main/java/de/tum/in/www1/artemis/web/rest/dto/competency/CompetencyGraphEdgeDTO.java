package de.tum.in.www1.artemis.web.rest.dto.competency;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.competency.CompetencyRelation;
import de.tum.in.www1.artemis.domain.competency.RelationType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyGraphEdgeDTO(String id, String source, String target, RelationType relationType) {

    public static CompetencyGraphEdgeDTO of(CompetencyRelation competencyRelation) {
        return new CompetencyGraphEdgeDTO(competencyRelation.getId().toString(), competencyRelation.getHeadCompetency().getId().toString(),
                competencyRelation.getTailCompetency().getId().toString(), competencyRelation.getType());
    }
}
