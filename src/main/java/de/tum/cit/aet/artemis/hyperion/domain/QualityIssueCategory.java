package de.tum.cit.aet.artemis.hyperion.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Category of a quality issue found in the problem statement.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public enum QualityIssueCategory {

    CLARITY, COHERENCE, COMPLETENESS;
}
