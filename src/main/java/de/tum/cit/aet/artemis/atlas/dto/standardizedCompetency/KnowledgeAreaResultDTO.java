package de.tum.cit.aet.artemis.atlas.dto.standardizedCompetency;

import java.util.List;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.KnowledgeArea;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Knowledge area read data with initialized descendants and competencies.
 *
 * @param id           persisted knowledge area identifier
 * @param title        title, omitted when empty
 * @param shortTitle   short title, omitted when absent or empty
 * @param description  description, omitted when absent or empty
 * @param parentId     parent identifier, omitted for roots
 * @param children     children, omitted when uninitialized or empty
 * @param competencies competencies, omitted when uninitialized or empty
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record KnowledgeAreaResultDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id, @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Nullable String title,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Nullable String shortTitle, @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Nullable String description,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Nullable Long parentId,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Nullable List<KnowledgeAreaResultDTO> children,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Nullable List<StandardizedCompetencyResultDTO> competencies) {

    /**
     * Creates a KnowledgeAreaResultDTO from the given KnowledgeArea
     *
     * @param knowledgeArea the KnowledgeArea
     * @return the created KnowledgeAreaResultDTO
     */
    public static KnowledgeAreaResultDTO of(KnowledgeArea knowledgeArea) {
        @Nullable
        Long parentId = knowledgeArea.getParent() == null ? null : knowledgeArea.getParent().getId();
        @Nullable
        List<KnowledgeAreaResultDTO> children = null;
        if (Hibernate.isInitialized(knowledgeArea.getChildren()) && knowledgeArea.getChildren() != null) {
            children = knowledgeArea.getChildren().stream().map(KnowledgeAreaResultDTO::of).toList();
        }
        @Nullable
        List<StandardizedCompetencyResultDTO> competencies = null;
        if (Hibernate.isInitialized(knowledgeArea.getCompetencies()) && knowledgeArea.getCompetencies() != null) {
            competencies = knowledgeArea.getCompetencies().stream().map(StandardizedCompetencyResultDTO::of).toList();
        }

        return new KnowledgeAreaResultDTO(knowledgeArea.getId(), knowledgeArea.getTitle(), knowledgeArea.getShortTitle(), knowledgeArea.getDescription(), parentId, children,
                competencies);
    }
}
