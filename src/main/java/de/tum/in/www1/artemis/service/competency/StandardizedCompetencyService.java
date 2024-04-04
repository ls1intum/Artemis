package de.tum.in.www1.artemis.service.competency;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import jakarta.ws.rs.BadRequestException;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.competency.KnowledgeArea;
import de.tum.in.www1.artemis.domain.competency.Source;
import de.tum.in.www1.artemis.domain.competency.StandardizedCompetency;
import de.tum.in.www1.artemis.repository.SourceRepository;
import de.tum.in.www1.artemis.repository.competency.KnowledgeAreaRepository;
import de.tum.in.www1.artemis.repository.competency.StandardizedCompetencyRepository;

/**
 * Service for managing {@link StandardizedCompetency} entities.
 */
@Profile(PROFILE_CORE)
@Service
public class StandardizedCompetencyService {

    private static final String FIRST_VERSION = "1.0.0";

    private final StandardizedCompetencyRepository standardizedCompetencyRepository;

    private final KnowledgeAreaRepository knowledgeAreaRepository;

    private final SourceRepository sourceRepository;

    public StandardizedCompetencyService(StandardizedCompetencyRepository standardizedCompetencyRepository, KnowledgeAreaRepository knowledgeAreaRepository,
            SourceRepository sourceRepository) {
        this.standardizedCompetencyRepository = standardizedCompetencyRepository;
        this.knowledgeAreaRepository = knowledgeAreaRepository;
        this.sourceRepository = sourceRepository;
    }

    /**
     * Verifies a standardized competency and then saves it to the database
     *
     * @param competency the standardized competency to create
     * @return the created standardized competency
     */
    public StandardizedCompetency createStandardizedCompetency(StandardizedCompetency competency) {
        standardizedCompetencyIsValidOrElseThrow(competency);

        // fetch the knowledge area and source from the database if they exists
        KnowledgeArea knowledgeArea = competency.getKnowledgeArea();
        if (knowledgeArea != null) {
            knowledgeArea = knowledgeAreaRepository.findByIdElseThrow(knowledgeArea.getId());
        }
        Source source = competency.getSource();
        if (source != null) {
            source = sourceRepository.findByIdElseThrow(source.getId());
        }

        var competencyToCreate = new StandardizedCompetency(competency.getTitle().trim(), competency.getDescription(), competency.getTaxonomy(), FIRST_VERSION);
        competencyToCreate.setKnowledgeArea(knowledgeArea);
        competencyToCreate.setSource(source);

        competencyToCreate = standardizedCompetencyRepository.save(competencyToCreate);
        // set the reference to the first version of this standardized competency (itself)
        competencyToCreate.setFirstVersion(competencyToCreate);
        return standardizedCompetencyRepository.save(competencyToCreate);
    }

    /**
     * Verifies that a standardized competency that should be created is valid or throws a BadRequestException
     *
     * @param competency the standardized competency to verify
     */
    private void standardizedCompetencyIsValidOrElseThrow(StandardizedCompetency competency) throws BadRequestException {
        boolean TitleIsInvalid = competency.getTitle() == null || competency.getTitle().trim().isEmpty()
                || competency.getTitle().length() > StandardizedCompetency.MAX_TITLE_LENGTH;
        boolean DescriptionIsInvalid = competency.getDescription() != null && competency.getDescription().length() > StandardizedCompetency.MAX_DESCRIPTION_LENGTH;
        boolean knowledgeAreaIsInvalid = competency.getKnowledgeArea() == null;

        if (TitleIsInvalid || DescriptionIsInvalid || knowledgeAreaIsInvalid) {
            throw new BadRequestException();
        }
    }
}
