package de.tum.cit.aet.artemis.programming.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The Security Framework activation state of a programming exercise, as sent to the client. Mirrors the
 * client-side {@code SecurityFrameworkConfig}. {@code lastCommitHash}/{@code lastSyncedAt} are only set
 * once a policy has been committed.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SecurityFrameworkConfigDTO(String status, String frameworkVersion, String lastCommitHash, String lastSyncedAt) {
}
