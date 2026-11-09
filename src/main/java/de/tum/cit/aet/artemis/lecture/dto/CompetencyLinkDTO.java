package de.tum.cit.aet.artemis.lecture.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyLearningObjectLink;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A competency link of an exercise or lecture unit.
 *
 * @param competency    the linked competency
 * @param weight        the relevance of the link; a primitive, so it is always serialized
 * @param generatedByAi whether the link was suggested by AI, only ever sent by the server
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyLinkDTO(CompetencyDTO competency, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double weight,
        @JsonProperty(access = JsonProperty.Access.READ_ONLY) boolean generatedByAi) {

    public CompetencyLinkDTO(CompetencyDTO competency, double weight) {
        this(competency, weight, false);
    }

    public static CompetencyLinkDTO of(CompetencyLearningObjectLink link) {
        return new CompetencyLinkDTO(CompetencyDTO.of(link.getCompetency()), link.getWeight(), link.isGeneratedByAi());
    }
}
