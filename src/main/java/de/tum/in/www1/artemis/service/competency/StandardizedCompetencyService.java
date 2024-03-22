package de.tum.in.www1.artemis.service.competency;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import javax.ws.rs.BadRequestException;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.competency.KnowledgeArea;
import de.tum.in.www1.artemis.domain.competency.Source;
import de.tum.in.www1.artemis.domain.competency.StandardizedCompetency;
import de.tum.in.www1.artemis.repository.SourceRepository;
import de.tum.in.www1.artemis.repository.competency.KnowledgeAreaRepository;
import de.tum.in.www1.artemis.repository.competency.StandardizedCompetencyRepository;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

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
     * Updates an existing standardized competency with the provided competency data
     *
     * @param competency the new competency values
     * @return the updated standardized competency
     */
    public StandardizedCompetency updateStandardizedCompetency(StandardizedCompetency competency) {
        var existingCompetency = standardizedCompetencyRepository.findByIdElseThrow(competency.getId());

        if (competency.getVersion() != null && !competency.getVersion().equals(existingCompetency.getVersion())) {
            throw new BadRequestException("You cannot change the version of a standardized competency");
        }
        if (competency.getFirstVersion() != null && !competency.getFirstVersion().equals(existingCompetency.getFirstVersion())) {
            throw new BadRequestException("You cannot change the first version of a standardized competency");
        }

        existingCompetency.setTitle(competency.getTitle());
        existingCompetency.setDescription(competency.getDescription());
        existingCompetency.setTaxonomy(competency.getTaxonomy());
        if (competency.getSource() == null) {
            existingCompetency.setSource(null);
        }
        else if (!competency.getSource().equals(existingCompetency.getSource())) {
            var source = sourceRepository.findByIdElseThrow(competency.getSource().getId());
            existingCompetency.setSource(source);
        }
        if (competency.getKnowledgeArea() == null) {
            existingCompetency.setKnowledgeArea(null);
        }
        else if (!competency.getKnowledgeArea().equals(existingCompetency.getKnowledgeArea())) {
            var knowledgeArea = knowledgeAreaRepository.findByIdElseThrow(competency.getKnowledgeArea().getId());
            existingCompetency.setKnowledgeArea(knowledgeArea);
        }

        return standardizedCompetencyRepository.save(existingCompetency);
    }

    /**
     * Deletes an existing standardized competency with the given id or throws an EntityNotFoundException
     *
     * @param competencyId the id of the competency to delete
     */
    public void deleteStandardizedCompetencyElseThrow(long competencyId) throws EntityNotFoundException {
        if (!standardizedCompetencyRepository.existsById(competencyId)) {
            throw new EntityNotFoundException("StandardizedCompetency", competencyId);
        }
        standardizedCompetencyRepository.deleteById(competencyId);
    }

    /**
     * Verifies that a standardized competency that should be created is valid or throws a BadRequestException
     *
     * @param competency the standardized competency to verify
     */
    private void standardizedCompetencyIsValidOrElseThrow(StandardizedCompetency competency) throws BadRequestException {
        if (competency.getId() != null || competency.getTitle() == null || competency.getTitle().trim().isEmpty()) {
            throw new BadRequestException();
        }
    }
}
