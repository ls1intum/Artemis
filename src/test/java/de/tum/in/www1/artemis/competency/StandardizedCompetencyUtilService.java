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
     * builds a new standardized competency with the given parameters
     *
     * @param title         the competency title
     * @param description   the competency description
     * @param taxonomy      the competency taxonomy
     * @param version       the competency version
     * @param knowledgeArea the knowledgeArea that the competency is part of
     * @param source        the source of the competency
     * @return the standardized competency
     */
    public static StandardizedCompetency buildStandardizedCompetency(String title, String description, CompetencyTaxonomy taxonomy, String version, KnowledgeArea knowledgeArea,
            Source source) {
        var competency = new StandardizedCompetency(title, description, taxonomy, version);
        competency.setKnowledgeArea(knowledgeArea);
        competency.setSource(source);
        return competency;
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
        var competency = buildStandardizedCompetency(title, description, taxonomy, version, knowledgeArea, source);
        return standardizedCompetencyRepository.save(competency);
    }

    static class CheckStandardizedCompetencyValidationProvider implements ArgumentsProvider {

        @Override
        public Stream<Arguments> provideArguments(ExtensionContext extensionContext) {
            // competencies need a knowledge area.
            // use a non-existing id to see that the validation is executed before the knowledge area gets retrieved.
            var kaNotExisting = new KnowledgeArea();
            kaNotExisting.setId(ID_NOT_EXISTS);
            var kaNoId = new KnowledgeArea();

            var competencies = new ArrayList<StandardizedCompetency>();
            // invalid title
            competencies.add(buildStandardizedCompetency("", "valid description", null, null, kaNotExisting, null));
            competencies.add(buildStandardizedCompetency(null, "valid description", null, null, kaNotExisting, null));
            competencies.add(buildStandardizedCompetency("0".repeat(StandardizedCompetency.MAX_TITLE_LENGTH + 1), "valid description", null, null, kaNotExisting, null));
            // invalid description
            competencies.add(buildStandardizedCompetency("valid title", "0".repeat(StandardizedCompetency.MAX_DESCRIPTION_LENGTH + 1), null, null, kaNotExisting, null));
            // invalid knowledge area
            competencies.add(buildStandardizedCompetency("valid title", "valid description", null, null, kaNoId, null));
            competencies.add(buildStandardizedCompetency("valid title", "valid description", null, null, null, null));

            return competencies.stream().map(Arguments::of);
        }
    }

    static class CheckKnowledgeAreaValidationProvider implements ArgumentsProvider {

        @Override
        public Stream<Arguments> provideArguments(ExtensionContext extensionContext) {
            var knowledgeAreas = new ArrayList<KnowledgeArea>();
            // invalid title
            knowledgeAreas.add(new KnowledgeArea("", "shortTitle", ""));
            knowledgeAreas.add(new KnowledgeArea(null, "shortTitle", ""));
            knowledgeAreas.add(new KnowledgeArea("0".repeat(KnowledgeArea.MAX_TITLE_LENGTH + 1), "shortTitle", ""));
            // invalid short title
            knowledgeAreas.add(new KnowledgeArea("title", "", ""));
            knowledgeAreas.add(new KnowledgeArea("title", "0".repeat(KnowledgeArea.MAX_SHORT_TITLE_LENGTH + 1), ""));
            // invalid description
            knowledgeAreas.add(new KnowledgeArea("title", "shortTitle", "0".repeat(KnowledgeArea.MAX_DESCRIPTION_LENGTH + 1)));
            return knowledgeAreas.stream().map(Arguments::of);
        }
    }
}
