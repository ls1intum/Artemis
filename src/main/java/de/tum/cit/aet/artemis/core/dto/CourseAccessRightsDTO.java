package de.tum.cit.aet.artemis.core.dto;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.CourseRole;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseAccessRightsDTO(long courseId, Set<CourseRole> roles) {
}
