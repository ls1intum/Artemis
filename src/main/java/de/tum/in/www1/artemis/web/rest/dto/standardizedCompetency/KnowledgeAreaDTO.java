package de.tum.in.www1.artemis.web.rest.dto.standardizedCompetency;

import java.util.List;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.competency.KnowledgeArea;

/**
 * DTO containing {@link KnowledgeArea} data. It only contains the id of its parent.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record KnowledgeAreaDTO(Long id, String title, String shortTitle, String description, Long parentId, List<KnowledgeAreaDTO> children,
        List<StandardizedCompetencyDTO> competencies) {

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
