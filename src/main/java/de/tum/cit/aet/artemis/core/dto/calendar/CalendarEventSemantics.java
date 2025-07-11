package de.tum.cit.aet.artemis.core.dto.calendar;

import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSession;

/**
 * <p>
 * {@link CalendarEventDTO}s represent events displayed in the calendar feature and are derived from either a {@link Lecture},
 * a {@link TutorialGroupSession}, an {@link Exam}, or one of the {@link Exercise} subtypes. Each of these entities contains
 * one or more date properties relevant to the calendar, which are encapsulated by the DTOs. A CalendarEventDTO can represent
 * either a single point in time (only startDate, no endDate) or a timespan (both startDate and endDate). Because many of the
 * entities have properties that represent the same concept (e.g. a release date, a start date, etc.) each DTOs can semantically
 * be mapped to one of the categories represented by this enum.
 * <p>
 * <p>
 * The semantic type is used to display localized context information about the event in the calendar instead of hard-coding it as
 * an english string. For example the title of each event with semantic type END_DATE is prefixed with "End:" in the calendar if local
 * is EN and with "Ende:" if local is DE.
 * <p>
 */
public enum CalendarEventSemantics {
    START_DATE, END_DATE, START_AND_END_DATE, RELEASE_DATE, DUE_DATE, PUBLISH_RESULTS_DATE, STUDENT_REVIEW_START_DATE, STUDENT_REVIEW_END_DATE, ASSESSMENT_DUE_DATE
}
