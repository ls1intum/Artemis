package de.tum.cit.aet.artemis.lecture.dto;

import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

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

    /**
     * Maps update-request links only when already loaded, preserving the distinction between absent and empty links.
     *
     * @param links links from the learning object
     * @return the mapped links, or null when absent or not initialized
     */
    public static @Nullable Set<CompetencyLinkDTO> ofAllIfInitialized(@Nullable Set<? extends CompetencyLearningObjectLink> links) {
        if (links == null || !Hibernate.isInitialized(links)) {
            return null;
        }
        return links.isEmpty() ? Set.of() : links.stream().map(CompetencyLinkDTO::of).collect(Collectors.toSet());
    }

    public static CompetencyLinkDTO of(CompetencyLearningObjectLink link) {
        return new CompetencyLinkDTO(CompetencyDTO.of(link.getCompetency()), link.getWeight(), link.isGeneratedByAi());
    }
}
