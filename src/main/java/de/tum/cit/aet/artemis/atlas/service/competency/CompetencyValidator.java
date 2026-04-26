package de.tum.cit.aet.artemis.atlas.service.competency;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;

/**
 * Centralized validation for competency attributes used both by the REST controller
 * ({@link de.tum.cit.aet.artemis.atlas.web.CompetencyResource}) and by the orchestrator's
 * write tools ({@link de.tum.cit.aet.artemis.atlas.service.OrchestratorToolsService}).
 * <p>
 * Throws {@link BadRequestAlertException} on rule violations so the REST layer can surface a
 * structured 400 response and the orchestrator can convert the message into a tool-call error
 * the LLM can react to.
 */
@Lazy
@Service
@Conditional(AtlasEnabled.class)
public class CompetencyValidator {

    public static final String ENTITY_NAME = "competency";

    /**
     * Title column is {@code varchar(255)} in the schema; truncating earlier produces a clear
     * error rather than an opaque {@code DataIntegrityViolationException}.
     */
    public static final int TITLE_MAX_LENGTH = 255;

    /**
     * Description column is {@code longtext} (unbounded), but a multi-kilobyte description is
     * almost always an LLM hallucination — cap at 10 000 chars so we fail fast with a clear
     * error instead of bloating the row.
     */
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
     * Validate the title (non-empty, within DB column length), description (within reasonable
     * length cap), and mastery threshold (1–100) of a competency. Throws
     * {@link BadRequestAlertException} on the first violation; intended to be called by both REST
     * controllers and the orchestrator's write tools so they share a single set of rules.
     *
     * @param competency the competency to validate; only its scalar attributes are inspected
     */
    public void checkAttributes(CourseCompetency competency) {
        String title = competency.getTitle();
        if (title == null || title.trim().isEmpty()) {
            throw new BadRequestAlertException("The title of a competency is invalid!", ENTITY_NAME, "invalidCompetencyTitle");
        }
        if (title.length() > TITLE_MAX_LENGTH) {
            throw new BadRequestAlertException(
                    "The title of the competency '" + title.substring(0, Math.min(40, title.length())) + "…' exceeds the maximum length of " + TITLE_MAX_LENGTH + " characters.",
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
