package de.tum.cit.aet.artemis.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO previewing the number of courses affected by an old-course data-privacy cleanup operation (warning/archiving or
 * resetting the student data), so an admin can see how many courses would be touched before confirming the action.
 *
 * @param courses the number of affected courses
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(requiredProperties = { "courses" })
public record OldCoursesCleanupCountDTO(int courses) {
}
