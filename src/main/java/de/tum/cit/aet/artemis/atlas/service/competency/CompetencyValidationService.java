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

    /** Matches the {@code varchar(255)} schema column; fail earlier than a {@code DataIntegrityViolationException}. */
    public static final int TITLE_MAX_LENGTH = 255;

    /** Description column is {@code longtext}; cap to fail fast on LLM-hallucinated multi-KB descriptions. */
    public static final int DESCRIPTION_MAX_LENGTH = 10_000;

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
     * Validates title, description, and mastery threshold; throws {@link BadRequestAlertException} on the first violation.
     *
     * @param competency the competency to validate
     */
    public void checkAttributes(CourseCompetency competency) {
        String title = competency.getTitle();
        if (title == null || title.isBlank()) {
            throw new BadRequestAlertException("The title of a competency is invalid!", ENTITY_NAME, "invalidCompetencyTitle");
        }
        if (title.length() > TITLE_MAX_LENGTH) {
            throw new BadRequestAlertException("The title of the competency '" + title.substring(0, 40) + "…' exceeds the maximum length of " + TITLE_MAX_LENGTH + " characters.",
                    ENTITY_NAME, "competencyTitleTooLong");
        }
        String description = competency.getDescription();
        if (description != null && description.length() > DESCRIPTION_MAX_LENGTH) {
            throw new BadRequestAlertException("The description of the competency '" + title + "' exceeds the maximum length of " + DESCRIPTION_MAX_LENGTH + " characters.",
                    ENTITY_NAME, "competencyDescriptionTooLong");
        }
        if (competency.getMasteryThreshold() < 1 || competency.getMasteryThreshold() > 100) {
            throw new BadRequestAlertException("The mastery threshold of the competency '" + title + "' is invalid!", ENTITY_NAME, "invalidCompetencyMasteryThreshold");
        }
    }
}
