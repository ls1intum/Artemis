package de.tum.cit.aet.artemis.calendar.dto;

import java.time.ZonedDateTime;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.calendar.domain.CoursewideCalendarEvent;

/**
 * A DTO used to perform CRUD operations on {@link CoursewideCalendarEvent}s
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CoursewideCalendarEventDTO(@Nullable String id, @NotNull(message = "Calendar events must have a title.") String title, @Nullable String courseName,
        @NotNull(message = "Calendar events must have a startDate.") ZonedDateTime startDate, @Nullable ZonedDateTime endDate, @Nullable String location,
        @Nullable String facilitator, boolean visibleToStudents, boolean visibleToTutors, boolean visibleToEditors, boolean visibleToInstructors) {

    public CoursewideCalendarEventDTO(CoursewideCalendarEvent event) {
        this("course-" + event.getId(), event.getTitle(), event.getCourse().getTitle(), event.getStartDate(), event.getEndDate() == null ? null : event.getEndDate(),
                event.getLocation(), event.getFacilitator(), event.isVisibleToStudents(), event.isVisibleToTutors(), event.isVisibleToEditors(), event.isVisibleToInstructors());
    }
}
