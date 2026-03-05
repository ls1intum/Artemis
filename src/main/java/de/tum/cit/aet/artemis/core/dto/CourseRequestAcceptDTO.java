package de.tum.cit.aet.artemis.core.dto;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for accepting a course request with admin-provided course data.
 * The admin can modify the course title, semester, dates and must provide the short name.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseRequestAcceptDTO(@NotBlank @Size(max = 255) String title, @NotBlank @Size(max = 255) String shortName, String semester, ZonedDateTime startDate,
        ZonedDateTime endDate) {
}
