package de.tum.cit.aet.artemis.web.rest.dto.standardizedCompetency;

import java.util.List;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.KnowledgeArea;

/**
 * DTO containing {@link KnowledgeArea} data. It only contains the id of the knowledge area and source.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record KnowledgeAreaResultDTO(Long id, String title, String shortTitle, String description, Long parentId, List<KnowledgeAreaResultDTO> children,
        List<StandardizedCompetencyResultDTO> competencies) {

    /**
     * Creates a KnowledgeAreaResultDTO from the given KnowledgeArea
     *
     * @param knowledgeArea the KnowledgeArea
     * @return the created KnowledgeAreaResultDTO
     */
    public static KnowledgeAreaResultDTO of(KnowledgeArea knowledgeArea) {
        Long parentId = knowledgeArea.getParent() == null ? null : knowledgeArea.getParent().getId();
        List<KnowledgeAreaResultDTO> children = null;
        if (Hibernate.isInitialized(knowledgeArea.getChildren()) && knowledgeArea.getChildren() != null) {
            children = knowledgeArea.getChildren().stream().map(KnowledgeAreaResultDTO::of).toList();
        }
        List<StandardizedCompetencyResultDTO> competencies = null;
        if (Hibernate.isInitialized(knowledgeArea.getCompetencies()) && knowledgeArea.getCompetencies() != null) {
            competencies = knowledgeArea.getCompetencies().stream().map(StandardizedCompetencyResultDTO::of).toList();
        }

        return new KnowledgeAreaResultDTO(knowledgeArea.getId(), knowledgeArea.getTitle(), knowledgeArea.getShortTitle(), knowledgeArea.getDescription(), parentId, children,
                competencies);
    }
}
