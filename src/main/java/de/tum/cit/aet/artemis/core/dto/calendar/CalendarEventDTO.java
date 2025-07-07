package de.tum.cit.aet.artemis.core.dto.calendar;

import java.time.ZonedDateTime;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * A DTO used to display calendar events in the calendar feature.
 */
@JsonInclude(Include.NON_EMPTY)
public record CalendarEventDTO(@NotNull CalendarEventType type, @NotNull CalendarEventSubtype subtype, @NotNull String title, @NotNull ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate, @Nullable String location, @Nullable String facilitator) {

    public CalendarEventDTO(String projectionTypeKey, String projectionSubtypeKey, String title, ZonedDateTime startDate, ZonedDateTime endDate, String location,
            String facilitator) {
        this(createTypeFromProjectionKey(projectionTypeKey), createSubtypeFromProjectionKey(projectionSubtypeKey), title, startDate, endDate, location, facilitator);
    }

    private static CalendarEventType createTypeFromProjectionKey(String key) {
        return switch (key) {
            case "lecture" -> CalendarEventType.LECTURE;
            case "tutorial" -> CalendarEventType.TUTORIAL;
            case "exam" -> CalendarEventType.EXAM;
            case "quizExercise" -> CalendarEventType.QUIZ_EXERCISE;
            case "textExercise" -> CalendarEventType.TEXT_EXERCISE;
            case "modelingExercise" -> CalendarEventType.MODELING_EXERCISE;
            case "programmingExercise" -> CalendarEventType.PROGRAMMING_EXERCISE;
            case "fileUploadExercise" -> CalendarEventType.FILE_UPLOAD_EXERCISE;
            default -> throw new IllegalArgumentException("Unknown projectionTypeKey: " + key);
        };
    }

    private static CalendarEventSubtype createSubtypeFromProjectionKey(String key) {
        return switch (key) {
            case "startDate" -> CalendarEventSubtype.START_DATE;
            case "endDate" -> CalendarEventSubtype.END_DATE;
            case "startAndEndDate" -> CalendarEventSubtype.START_AND_END_DATE;
            case "releaseDate" -> CalendarEventSubtype.RELEASE_DATE;
            case "dueDate" -> CalendarEventSubtype.DUE_DATE;
            case "publishResultsDate" -> CalendarEventSubtype.PUBLISH_RESULTS_DATE;
            case "studentReviewStartDate" -> CalendarEventSubtype.STUDENT_REVIEW_START_DATE;
            case "studentReviewEndDate" -> CalendarEventSubtype.STUDENT_REVIEW_END_DATE;
            case "assessmentDueDate" -> CalendarEventSubtype.ASSESSMENT_DUE_DATE;
            default -> throw new IllegalArgumentException("Unknown projectionSubtypeKey: " + key);
        };
    }
}
