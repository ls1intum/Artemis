package de.tum.cit.aet.artemis.atlas.domain.science;

import java.util.EnumSet;
import java.util.Set;

/**
 * Types of events that can be logged for scientific purposes.
 * <p>
 * Important: Please follow the naming convention <category>__<detailed event name>
 */
public enum ScienceEventType {

    LECTURE__OPEN, LECTURE__OPEN_UNIT, EXERCISE__OPEN, COMPETENCY__OPEN, COMPETENCY__OPEN_OVERVIEW, LEARNING_PATH__OPEN, LEARNING_PATH__OPEN_NAVIGATION, LEARNING_PATH__NAV_NEXT,
    LEARNING_PATH__NAV_PREV, LEARNING_PATH__OPEN_GRAPH, IRIS__ENTRYPOINT_IMPRESSION, IRIS__OPENED_SIDEBAR, SCIENCE__OPT_IN, SCIENCE__OPT_OUT, SCIENCE__DATA_DELETED;

    /** The events that record what the student decided about collection, as opposed to what they did. */
    public static final Set<ScienceEventType> CONSENT_DECISION_EVENT_TYPES = EnumSet.of(SCIENCE__OPT_IN, SCIENCE__OPT_OUT);

    /** The events that are never collected from an interaction, and that a per-course data deletion keeps. */
    public static final Set<ScienceEventType> AUDIT_EVENT_TYPES = EnumSet.of(SCIENCE__OPT_IN, SCIENCE__OPT_OUT, SCIENCE__DATA_DELETED);
}
