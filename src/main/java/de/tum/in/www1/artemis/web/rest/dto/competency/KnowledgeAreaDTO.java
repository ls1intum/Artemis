package de.tum.in.www1.artemis.web.rest.dto.competency;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.competency.KnowledgeArea;

/**
 * DTO containing {@link KnowledgeArea} data. It only contains the id of its parent.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record KnowledgeAreaDTO(long id, String title, String shortTitle, String description, Long parentId, List<KnowledgeAreaDTO> children,
        List<StandardizedCompetencyDTO> competencies) {

    /**
     * Creates a KnowledgeAreaDTO from the given KnowledgeArea
     *
     * @param knowledgeArea the KnowledgeArea
     * @return the created KnowledgeAreaDTO
     */
    public static KnowledgeAreaDTO of(KnowledgeArea knowledgeArea) {
        Long parentId = knowledgeArea.getParent() == null ? null : knowledgeArea.getParent().getId();
        var children = knowledgeArea.getChildren().stream().map(KnowledgeAreaDTO::of).toList();
        var competencies = knowledgeArea.getCompetencies().stream().map(StandardizedCompetencyDTO::of).toList();

        return new KnowledgeAreaDTO(knowledgeArea.getId(), knowledgeArea.getTitle(), knowledgeArea.getShortTitle(), knowledgeArea.getDescription(), parentId, children,
                competencies);
    }
}
