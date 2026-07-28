package de.tum.cit.aet.artemis.iris.service.pyris.dto.search;

import java.time.ZonedDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

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
public record PyrisAccessContextDTO(@JsonProperty("courseIds") List<Long> courseIds, @JsonProperty("editorCourseIds") List<Long> editorCourseIds,
        @JsonProperty("taCourseIds") List<Long> taCourseIds, @JsonProperty("studentCourseIds") List<Long> studentCourseIds, @JsonProperty("staffCourseIds") List<Long> staffCourseIds,
        @JsonProperty("now") ZonedDateTime now, @JsonProperty("unrestricted") boolean unrestricted) {
}
