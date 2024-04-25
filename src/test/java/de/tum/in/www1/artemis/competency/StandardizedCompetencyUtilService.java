package de.tum.in.www1.artemis.competency;

import java.util.ArrayList;
import java.util.stream.Stream;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.competency.CompetencyTaxonomy;
import de.tum.in.www1.artemis.domain.competency.KnowledgeArea;
import de.tum.in.www1.artemis.domain.competency.Source;
import de.tum.in.www1.artemis.domain.competency.StandardizedCompetency;
import de.tum.in.www1.artemis.repository.competency.KnowledgeAreaRepository;
import de.tum.in.www1.artemis.repository.competency.StandardizedCompetencyRepository;
import de.tum.in.www1.artemis.web.rest.dto.standardizedCompetency.KnowledgeAreaRequestDTO;
import de.tum.in.www1.artemis.web.rest.dto.standardizedCompetency.StandardizedCompetencyRequestDTO;

@Service
public class StandardizedCompetencyUtilService {

    public static final long ID_NOT_EXISTS = -1000L;

    @Autowired
    private KnowledgeAreaRepository knowledgeAreaRepository;

    @Autowired
    private StandardizedCompetencyRepository standardizedCompetencyRepository;

    /**
     * builds a new knowledge area with the given parameters and saves it to the database
     *
     * @param title       the knowledge area title
     * @param shortTitle  the knowledge area shortTitle
     * @param description the knowledge area description
     * @param parent      the parent knowledge area
     *
     * @return the persisted knowledge area
     */
    public KnowledgeArea saveKnowledgeArea(String title, String shortTitle, String description, KnowledgeArea parent) {
        var knowledgeArea = new KnowledgeArea(title, shortTitle, description);
        knowledgeArea.setParent(parent);
        return knowledgeAreaRepository.save(knowledgeArea);
    }

    /**
     * builds a new standardized competency with the given parameters and saves it to the database
     *
     * @param title         the competency title
     * @param description   the competency description
     * @param taxonomy      the competency taxonomy
     * @param version       the competency version
     * @param knowledgeArea the knowledgeArea that the competency is part of
     * @param source        the source of the competency
     * @return the persisted standardized competency
     */
    public StandardizedCompetency saveStandardizedCompetency(String title, String description, CompetencyTaxonomy taxonomy, String version, KnowledgeArea knowledgeArea,
            Source source) {
        var competency = new StandardizedCompetency(title, description, taxonomy, version);
        competency.setKnowledgeArea(knowledgeArea);
        competency.setSource(source);
        return standardizedCompetencyRepository.save(competency);
    }

    /**
     * Creates a StandardizedCompetencyRequestDTO from the given StandardizedCompetency
     *
     * @param competency the StandardizedCompetency
     * @return the created StandardizedCompetencyRequestDTO
     */
    public static StandardizedCompetencyRequestDTO toDTO(StandardizedCompetency competency) {
        Long sourceId = competency.getSource() == null ? null : competency.getSource().getId();
        Long knowledgeAreaId = competency.getKnowledgeArea() == null ? null : competency.getKnowledgeArea().getId();

        return new StandardizedCompetencyRequestDTO(competency.getTitle(), competency.getDescription(), competency.getTaxonomy(), knowledgeAreaId, sourceId);
    }

    /**
     * Creates a KnowledgeAreaRequestDTO from the given KnowledgeArea
     *
     * @param knowledgeArea the KnowledgeArea
     * @return the created KnowledgeAreaRequestDTO
     */
    public static KnowledgeAreaRequestDTO toDTO(KnowledgeArea knowledgeArea) {
        Long parentId = knowledgeArea.getParent() == null ? null : knowledgeArea.getParent().getId();

        return new KnowledgeAreaRequestDTO(knowledgeArea.getTitle(), knowledgeArea.getShortTitle(), knowledgeArea.getDescription(), parentId);
    }

    static class CheckStandardizedCompetencyValidationProvider implements ArgumentsProvider {

        @Override
        public Stream<Arguments> provideArguments(ExtensionContext extensionContext) {
            var competencies = new ArrayList<StandardizedCompetencyRequestDTO>();
            // invalid title
            competencies.add(new StandardizedCompetencyRequestDTO("", "valid description", null, ID_NOT_EXISTS, null));
            competencies.add(new StandardizedCompetencyRequestDTO(null, "valid description", null, ID_NOT_EXISTS, null));
            competencies.add(new StandardizedCompetencyRequestDTO("0".repeat(StandardizedCompetency.MAX_TITLE_LENGTH + 1), "valid description", null, ID_NOT_EXISTS, null));
            // invalid description
            competencies.add(new StandardizedCompetencyRequestDTO("valid title", "0".repeat(StandardizedCompetency.MAX_DESCRIPTION_LENGTH + 1), null, ID_NOT_EXISTS, null));
            // invalid knowledge area
            competencies.add(new StandardizedCompetencyRequestDTO("valid title", "valid description", null, null, null));

            return competencies.stream().map(Arguments::of);
        }
    }

    static class CheckKnowledgeAreaValidationProvider implements ArgumentsProvider {

        @Override
        public Stream<Arguments> provideArguments(ExtensionContext extensionContext) {
            var knowledgeAreas = new ArrayList<KnowledgeAreaRequestDTO>();
            // invalid title
            knowledgeAreas.add(new KnowledgeAreaRequestDTO("", "shortTitle", "", null));
            knowledgeAreas.add(new KnowledgeAreaRequestDTO(null, "shortTitle", "", null));
            knowledgeAreas.add(new KnowledgeAreaRequestDTO("0".repeat(KnowledgeArea.MAX_TITLE_LENGTH + 1), "shortTitle", "", null));
            // invalid short title
            knowledgeAreas.add(new KnowledgeAreaRequestDTO("title", "", "", null));
            knowledgeAreas.add(new KnowledgeAreaRequestDTO("title", "0".repeat(KnowledgeArea.MAX_SHORT_TITLE_LENGTH + 1), "", null));
            // invalid description
            knowledgeAreas.add(new KnowledgeAreaRequestDTO("title", "shortTitle", "0".repeat(KnowledgeArea.MAX_DESCRIPTION_LENGTH + 1), null));
            return knowledgeAreas.stream().map(Arguments::of);
        }
    }
}
