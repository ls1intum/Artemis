package de.tum.cit.aet.artemis.metis.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The registration of one student in one tutorial group of a course, with the title of the group. A projection of
 * {@code MetisGroupWindowRepository.findGroupMembershipsByCourseId}; no endpoint returns it.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MetisGroupMembershipDTO(long studentId, long tutorialGroupId, String title) {
}
