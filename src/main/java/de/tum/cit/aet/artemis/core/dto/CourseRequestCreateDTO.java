package de.tum.cit.aet.artemis.core.dto;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseRequestCreateDTO(@NotBlank @Size(max = 255) String title, @NotBlank @Size(max = 255) String shortName, @NotBlank String semester, ZonedDateTime startDate,
        ZonedDateTime endDate, boolean testCourse, @NotBlank String reason) {
}
