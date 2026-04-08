package de.tum.cit.aet.artemis.lecture.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyLearningObjectLink;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyLinkDTO(CompetencyDTO competency, double weight) {

    public static CompetencyLinkDTO of(CompetencyLearningObjectLink link) {
        return new CompetencyLinkDTO(CompetencyDTO.of(link.getCompetency()), link.getWeight());
    }
}
