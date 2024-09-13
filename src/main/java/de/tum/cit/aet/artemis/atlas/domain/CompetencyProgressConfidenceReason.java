package de.tum.cit.aet.artemis.atlas.domain;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyProgress;

/**
 * Enum to define the different reasons why the confidence is above/below 1 in the {@link CompetencyProgress}.
 * A confidence != 1 leads to a higher/lower mastery, which is displayed to the student together with the reason.
 * Also see {@link CompetencyProgress#setConfidenceReason}.
 */
public enum CompetencyProgressConfidenceReason {
    NO_REASON, RECENT_SCORES_LOWER, RECENT_SCORES_HIGHER, MORE_EASY_POINTS, MORE_HARD_POINTS, QUICKLY_SOLVED_EXERCISES
}
