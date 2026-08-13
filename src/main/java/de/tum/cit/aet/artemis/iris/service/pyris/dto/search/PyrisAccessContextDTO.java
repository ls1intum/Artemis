package de.tum.cit.aet.artemis.iris.service.pyris.dto.search;

import java.time.ZonedDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Role-grouped course IDs the requesting user can access, resolved by Artemis and applied by Pyris as opaque filters.
 * <p>
 * Serialized with {@link JsonInclude.Include#NON_EMPTY}: empty course lists and a {@code null} timestamp are omitted, so Iris defaults absent lists to empty. Admins are sent as a
 * present context with {@code unrestricted = true} (course lists may be empty) instead of a {@code null} context, which now means "apply the safe-default visibility filter".
 *
 * @param courseIds        every course the user can access (the union of the role groups)
 * @param editorCourseIds  courses where the user is at least an editor
 * @param taCourseIds      courses where the user is at least a teaching assistant but not an editor
 * @param studentCourseIds courses where the user is only a student
 * @param staffCourseIds   editor plus teaching-assistant courses (the release/visibility bypass group)
 * @param now              the Artemis request timestamp used for release-date filters; Iris falls back to its own UTC clock when absent
 * @param unrestricted     {@code true} for admins, who bypass course scoping and visibility filtering entirely
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisAccessContextDTO(List<Long> courseIds, List<Long> editorCourseIds, List<Long> taCourseIds, List<Long> studentCourseIds, List<Long> staffCourseIds,
        ZonedDateTime now, boolean unrestricted) {

    /**
     * Normalizes every role-grouped course-ID list to a non-null (empty) list, so callers and JSON
     * serialization never have to reason about {@code null} lists. {@code now} may still be {@code null}
     * (serialized as absent via {@link JsonInclude.Include#NON_EMPTY}; Iris then falls back to its own clock).
     */
    public PyrisAccessContextDTO {
        courseIds = courseIds == null ? List.of() : courseIds;
        editorCourseIds = editorCourseIds == null ? List.of() : editorCourseIds;
        taCourseIds = taCourseIds == null ? List.of() : taCourseIds;
        studentCourseIds = studentCourseIds == null ? List.of() : studentCourseIds;
        staffCourseIds = staffCourseIds == null ? List.of() : staffCourseIds;
    }
}
