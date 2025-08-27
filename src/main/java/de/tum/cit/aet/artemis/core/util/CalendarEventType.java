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

    LECTURE("Vorlesung", "Lecture"), TUTORIAL("Tutorium", "Tutorial"), EXAM("Klausur", "Exam"), QUIZ_EXERCISE("Quizaufgabe", "Quiz Exercise"),
    TEXT_EXERCISE("Textaufgabe", "Text Exercise"), MODELING_EXERCISE("Modellierungsaufgabe", "Modeling Exercise"),
    PROGRAMMING_EXERCISE("Programmieraufgabe", "Programming Exercise"), FILE_UPLOAD_EXERCISE("Datei-Upload-Aufgabe", "File-Upload Exercise");

    private final String germanDescription;

    private final String englishDescription;

    CalendarEventType(String germanDescription, String englishDescription) {
        this.germanDescription = germanDescription;
        this.englishDescription = englishDescription;
    }

    public String getGermanDescription() {
        return germanDescription;
    }

    public String getEnglishDescription() {
        return englishDescription;
    }
}
