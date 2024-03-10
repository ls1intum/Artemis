package de.tum.in.www1.artemis.service.competency;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import javax.ws.rs.BadRequestException;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.competency.StandardizedCompetency;
import de.tum.in.www1.artemis.repository.StandardizedCompetencyRepository;

/**
 * Service for managing {@link StandardizedCompetency} entities.
 */
@Profile(PROFILE_CORE)
@Service
public class StandardizedCompetencyService {

    private static final String FIRST_VERSION = "1.0.0";

    private final StandardizedCompetencyRepository standardizedCompetencyRepository;

    public StandardizedCompetencyService(StandardizedCompetencyRepository standardizedCompetencyRepository) {
        this.standardizedCompetencyRepository = standardizedCompetencyRepository;
    }

    public StandardizedCompetency createStandardizedCompetency(StandardizedCompetency competency) {
        var competencyToCreate = getStandardizedCompetencyToCreate(competency);
        return standardizedCompetencyRepository.save(competencyToCreate);
    }

    /**
     * Verifies that a standardized competency that should be created is valid or throws a BadRequestException
     *
     * @param competency the standardized competency to verify
     */
    public void standardizedCompetencyIsValidOrElseThrow(StandardizedCompetency competency) throws BadRequestException {
        if (competency.getId() != null || competency.getTitle() == null || competency.getTitle().trim().isEmpty()) {
            throw new BadRequestException();
        }
    }

    /**
     * Gets a new standardized competency from an existing one.
     * <p>
     * The competency is not persisted.
     *
     * @param competency the existing standardized competency
     * @return the new competency
     */
    public StandardizedCompetency getStandardizedCompetencyToCreate(StandardizedCompetency competency) {
        return new StandardizedCompetency(competency.getTitle().trim(), competency.getDescription(), competency.getTaxonomy(), FIRST_VERSION);
    }
}
