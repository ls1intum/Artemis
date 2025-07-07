package de.tum.cit.aet.artemis.exam.domain.room;

import org.springframework.context.annotation.Conditional;

import de.tum.cit.aet.artemis.exam.config.ExamEnabled;

/**
 * Enum representing the type of a specific layout strategy.
 */
@Conditional(ExamEnabled.class)
public enum LayoutStrategyType {
    FIXED_SELECTION, RELATIVE_DISTANCE
}
