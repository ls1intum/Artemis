package de.tum.cit.aet.artemis.hyperion.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The suggested difficulty level for a programming exercise.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public enum SuggestedDifficulty {

    EASY, MEDIUM, HARD, UNKNOWN;
}
