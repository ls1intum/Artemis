package de.tum.cit.aet.artemis.metis.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The window of an exercise for one tutorial group that a student is registered in, with the title of the group. A projection of
 * {@code MetisGroupWindowRepository.findGroupWindowsOfStudent}; no endpoint returns it.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MetisStudentGroupWindowDTO(long tutorialGroupId, String title, ZonedDateTime windowStart, ZonedDateTime windowEnd) {
}
