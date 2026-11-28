package de.tum.cit.aet.artemis.core.dto;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.CourseRole;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseAccessRightsDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long courseId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Set<CourseRole> roles) {
}
