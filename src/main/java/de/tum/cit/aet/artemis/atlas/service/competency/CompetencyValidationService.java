package de.tum.cit.aet.artemis.atlas.service.competency;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;

/**
 * Centralized validation for competency attributes shared by the REST controller and the
 * orchestrator's write tools. Throws {@link BadRequestAlertException} on rule violations.
 */
@Lazy
@Service
@Conditional(AtlasEnabled.class)
public class CompetencyValidationService {

    public static final String ENTITY_NAME = "competency";

    public void checkForCreation(CourseCompetency competency) {
        if (competency.getId() != null) {
            throw new BadRequestAlertException("A new competency should not have an id", ENTITY_NAME, "existingCompetencyId");
        }
        checkAttributes(competency);
    }

    public void checkForUpdate(CourseCompetency competency) {
        if (competency.getId() == null) {
            throw new BadRequestAlertException("An updated competency should have an id", ENTITY_NAME, "missingCompetencyId");
        }
        checkAttributes(competency);
    }

    /**
     * Throws {@link BadRequestAlertException} on the first attribute violation.
     *
     * @param competency the competency to validate
     */
    public void checkAttributes(CourseCompetency competency) {
        String title = competency.getTitle();
        if (title == null || title.isBlank()) {
            throw new BadRequestAlertException("The title of a competency is invalid!", ENTITY_NAME, "invalidCompetencyTitle");
        }
        if (competency.getMasteryThreshold() < 1 || competency.getMasteryThreshold() > 100) {
            throw new BadRequestAlertException("The mastery threshold of the competency '" + title + "' is invalid!", ENTITY_NAME, "invalidCompetencyMasteryThreshold");
        }
    }
}
