package de.tum.cit.aet.artemis.calendar.dto;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.calendar.domain.CourseCalendarEvent;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CalendarEventWriteDTO(@Nullable String id, @NotNull(message = "Calendar events must have a title.") String title, @Nullable String courseName,
        @NotNull(message = "Calendar events must have a startDate.") ZonedDateTime startDate, @Nullable ZonedDateTime endDate, @Nullable String location,
        @Nullable String facilitator, boolean visibleToStudents, boolean visibleToTutors, boolean visibleToEditors, boolean visibleToInstructors) {

    public CalendarEventWriteDTO(CourseCalendarEvent event) {
        this("course-" + event.getId(), event.getTitle(), event.getCourse().getTitle(), event.getStartDate().withZoneSameInstant(ZoneOffset.UTC),
                event.getEndDate() == null ? null : event.getEndDate().withZoneSameInstant(ZoneOffset.UTC), event.getLocation(), event.getFacilitator(),
                event.isVisibleToStudents(), event.isVisibleToTutors(), event.isVisibleToEditors(), event.isVisibleToInstructors());
    }
}
