package de.tum.cit.aet.artemis.core.util;

import de.tum.cit.aet.artemis.core.dto.calendar.CalendarEventDTO;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSession;

/**
 * {@link CalendarEventDTO}s represent events displayed in the calendar feature and are derived from either a {@link Lecture},
 * a {@link TutorialGroupSession}, an {@link Exam}, or one of the {@link Exercise} subtypes. This enum indicates from which
 * entity a given DTO was derived. The value is used for filtering and coloring of events in the calendar feature.
 */
public enum CalendarEventType {
    LECTURE, TUTORIAL, EXAM, QUIZ_EXERCISE, TEXT_EXERCISE, MODELING_EXERCISE, PROGRAMMING_EXERCISE, FILE_UPLOAD_EXERCISE
}
