package de.tum.cit.aet.artemis.core.dto;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.config.Constants;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseRequestAcceptDTO(@NotBlank @Size(max = 255) String title, @NotBlank @Size(min = 3) @Pattern(regexp = Constants.SHORT_NAME_REGEX) String shortName,
        @NotBlank String semester, ZonedDateTime startDate, ZonedDateTime endDate, boolean testCourse) {
}
