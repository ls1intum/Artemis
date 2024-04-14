package de.tum.in.www1.artemis.web.rest.dto.standardizedCompetency;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.in.www1.artemis.domain.competency.KnowledgeArea;

/**
 * DTO containing {@link KnowledgeArea} data. It only contains the id of its parent.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record KnowledgeAreaDTO(@JsonProperty(access = JsonProperty.Access.READ_ONLY) Long id, @NotNull @Size(min = 1, max = KnowledgeArea.MAX_TITLE_LENGTH) String title,
        @NotNull @Size(min = 1, max = KnowledgeArea.MAX_SHORT_TITLE_LENGTH) String shortTitle, @Size(max = KnowledgeArea.MAX_DESCRIPTION_LENGTH) String description, Long parentId,
        List<@Valid KnowledgeAreaDTO> children, List<@Valid StandardizedCompetencyDTO> competencies) {

    /**
     * Creates a KnowledgeAreaDTO from the given KnowledgeArea
     *
     * @param knowledgeArea the KnowledgeArea
     * @return the created KnowledgeAreaDTO
     */
    public static KnowledgeAreaDTO of(KnowledgeArea knowledgeArea) {
        Long parentId = knowledgeArea.getParent() == null ? null : knowledgeArea.getParent().getId();
        List<KnowledgeAreaDTO> children = null;
        if (Hibernate.isInitialized(knowledgeArea.getChildren()) && knowledgeArea.getChildren() != null) {
            children = knowledgeArea.getChildren().stream().map(KnowledgeAreaDTO::of).toList();
        }
        List<StandardizedCompetencyDTO> competencies = null;
        if (Hibernate.isInitialized(knowledgeArea.getCompetencies()) && knowledgeArea.getCompetencies() != null) {
            competencies = knowledgeArea.getCompetencies().stream().map(StandardizedCompetencyDTO::of).toList();
        }

        return new KnowledgeAreaDTO(knowledgeArea.getId(), knowledgeArea.getTitle(), knowledgeArea.getShortTitle(), knowledgeArea.getDescription(), parentId, children,
                competencies);
    }
}
