package de.tum.cit.aet.artemis.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO representing the count of active students in a specific student group.
 * Used for the two-query optimization approach to count active students.
 *
 * @param studentGroupName the name of the student group
 * @param count            the number of active students in this group
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StudentGroupCountDTO(String studentGroupName, long count) {

}
