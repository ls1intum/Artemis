package de.tum.cit.aet.artemis.web.rest.dto.standardizedCompetency;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.atlas.domain.competency.KnowledgeArea;
import de.tum.cit.aet.artemis.atlas.domain.competency.Source;
import de.tum.cit.aet.artemis.atlas.domain.competency.StandardizedCompetency;

/**
 * DTO including a nested structure of knowledge areas (including their descendants and competencies), as well as a list of sources
 * This is used to import/export knowledge areas, standardized competencies and sources
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StandardizedCompetencyCatalogDTO(List<@Valid KnowledgeAreaForCatalogDTO> knowledgeAreas, List<SourceDTO> sources) {

    /**
     * Creates a StandardizedCompetencyCatalogDTO from the given KnowledgeAreas and Sources
     *
     * @param knowledgeAreas the KnowledgeAreas
     * @param sources        the Sources
     * @return the created StandardizedCompetencyForCatalogDTO
     */
    public static StandardizedCompetencyCatalogDTO of(List<KnowledgeArea> knowledgeAreas, List<Source> sources) {

        var knowledgeAreaDTOs = knowledgeAreas.stream().map(KnowledgeAreaForCatalogDTO::of).toList();
        var sourceDTOs = sources.stream().map(SourceDTO::of).toList();

        return new StandardizedCompetencyCatalogDTO(knowledgeAreaDTOs, sourceDTOs);
    }

    /**
     * DTO containing knowledge area data as well as its children and competencies
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record KnowledgeAreaForCatalogDTO(@NotNull @Size(min = 1, max = KnowledgeArea.MAX_TITLE_LENGTH) String title,
            @NotNull @Size(min = 1, max = KnowledgeArea.MAX_SHORT_TITLE_LENGTH) String shortTitle, @Size(max = KnowledgeArea.MAX_DESCRIPTION_LENGTH) String description,
            List<@Valid KnowledgeAreaForCatalogDTO> children, List<@Valid StandardizedCompetencyForCatalogDTO> competencies) {

        /**
         * Creates a StandardizedCompetencyForCatalogDTO from the given StandardizedCompetency
         *
         * @param knowledgeArea the StandardizedCompetency
         * @return the created StandardizedCompetencyForCatalogDTO
         */
        public static KnowledgeAreaForCatalogDTO of(KnowledgeArea knowledgeArea) {
            List<KnowledgeAreaForCatalogDTO> children = null;
            if (Hibernate.isInitialized(knowledgeArea.getChildren()) && knowledgeArea.getChildren() != null) {
                children = knowledgeArea.getChildren().stream().map(KnowledgeAreaForCatalogDTO::of).toList();
            }
            List<StandardizedCompetencyForCatalogDTO> competencies = null;
            if (Hibernate.isInitialized(knowledgeArea.getCompetencies()) && knowledgeArea.getCompetencies() != null) {
                competencies = knowledgeArea.getCompetencies().stream().map(StandardizedCompetencyForCatalogDTO::of).toList();
            }

            return new KnowledgeAreaForCatalogDTO(knowledgeArea.getTitle(), knowledgeArea.getShortTitle(), knowledgeArea.getDescription(), children, competencies);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record StandardizedCompetencyForCatalogDTO(@NotNull @Size(min = 1, max = StandardizedCompetency.MAX_TITLE_LENGTH) String title,
            @Size(max = StandardizedCompetency.MAX_DESCRIPTION_LENGTH) String description, CompetencyTaxonomy taxonomy,
            @Size(min = 1, max = StandardizedCompetency.MAX_VERSION_LENGTH) String version, Long sourceId) {

        /**
         * Creates a StandardizedCompetencyForCatalogDTO from the given StandardizedCompetency
         *
         * @param competency the StandardizedCompetency
         * @return the created StandardizedCompetencyForCatalogDTO
         */
        public static StandardizedCompetencyForCatalogDTO of(StandardizedCompetency competency) {
            Long sourceId = competency.getSource() == null ? null : competency.getSource().getId();

            return new StandardizedCompetencyForCatalogDTO(competency.getTitle(), competency.getDescription(), competency.getTaxonomy(), competency.getVersion(), sourceId);
        }
    }
}
