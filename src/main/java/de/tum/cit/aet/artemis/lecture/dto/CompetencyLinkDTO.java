package de.tum.cit.aet.artemis.lecture.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyLearningObjectLink;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyLinkDTO(CompetencyDTO competency, double weight, @JsonProperty(access = JsonProperty.Access.READ_ONLY) boolean generatedByAi) {

    public CompetencyLinkDTO(CompetencyDTO competency, double weight) {
        this(competency, weight, false);
    }

    public static CompetencyLinkDTO of(CompetencyLearningObjectLink link) {
        return new CompetencyLinkDTO(CompetencyDTO.of(link.getCompetency()), link.getWeight(), link.isGeneratedByAi());
    }
}
