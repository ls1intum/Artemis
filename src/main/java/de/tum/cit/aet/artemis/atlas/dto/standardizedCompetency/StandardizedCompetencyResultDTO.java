package de.tum.cit.aet.artemis.atlas.dto.standardizedCompetency;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.atlas.domain.competency.StandardizedCompetency;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Scalar standardized competency read data without entity relationships.
 *
 * @param id              persisted competency identifier
 * @param title           title, omitted when empty
 * @param description     description, omitted when absent or empty
 * @param taxonomy        taxonomy, omitted when absent
 * @param version         version, omitted when absent or empty
 * @param knowledgeAreaId knowledge area identifier, omitted when absent
 * @param sourceId        source identifier, omitted when absent
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StandardizedCompetencyResultDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Nullable String title, @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Nullable String description,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Nullable CompetencyTaxonomy taxonomy,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Nullable String version, @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Nullable Long knowledgeAreaId,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Nullable Long sourceId) {

    /**
     * Creates a StandardizedCompetencyResultDTO from the given StandardizedCompetency
     *
     * @param competency the StandardizedCompetency
     * @return the created StandardizedCompetencyResultDTO
     */
    public static StandardizedCompetencyResultDTO of(StandardizedCompetency competency) {
        @Nullable
        Long sourceId = competency.getSource() == null ? null : competency.getSource().getId();
        @Nullable
        Long knowledgeAreaId = competency.getKnowledgeArea() == null ? null : competency.getKnowledgeArea().getId();

        return new StandardizedCompetencyResultDTO(competency.getId(), competency.getTitle(), competency.getDescription(), competency.getTaxonomy(), competency.getVersion(),
                knowledgeAreaId, sourceId);
    }
}
