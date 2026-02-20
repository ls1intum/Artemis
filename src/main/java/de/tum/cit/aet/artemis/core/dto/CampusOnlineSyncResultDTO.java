package de.tum.cit.aet.artemis.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO representing the result of a CAMPUSOnline enrollment synchronization run.
 * Returned by both the scheduled sync and the manual admin API trigger.
 *
 * @param coursesSynced the number of courses that were successfully synced
 * @param coursesFailed the number of courses where the sync failed
 * @param usersAdded    the total number of users added across all synced courses
 * @param usersNotFound the total number of confirmed students that could not be matched to Artemis users
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CampusOnlineSyncResultDTO(int coursesSynced, int coursesFailed, int usersAdded, int usersNotFound) {
}
