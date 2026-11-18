package de.tum.cit.aet.artemis.course.dto;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The data of a course request, used both to create a request and to edit a pending one.
 *
 * @param gradeRelevant whether the results of the course count towards official grades. A test course can never be grade relevant. When omitted, it defaults to
 *                          {@code true} for a regular course and to {@code false} for a test course.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseRequestCreateDTO(@NotBlank @Size(max = 255) String title, @NotBlank @Size(max = 255) String shortName, @NotBlank @Size(max = 25) String semester,
        @NotNull ZonedDateTime startDate, @NotNull ZonedDateTime endDate, boolean testCourse, @Nullable Boolean gradeRelevant, @NotBlank String reason) {
}
