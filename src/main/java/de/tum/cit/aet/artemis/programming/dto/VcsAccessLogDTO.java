package de.tum.cit.aet.artemis.programming.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.VcsAccessLog;

/**
 * DTO representing a VCS access log entry.
 *
 * @param id                      The id of the access log entry.
 * @param userId                  The user's id associated with the access log event, null for an access by a build
 *                                    agent, which authenticates as a build job rather than as a person.
 * @param name                    The name associated with the user, or the build agent and build job.
 * @param email                   The email associated with the user.
 * @param repositoryActionType    The type of action performed in the repository (read or write).
 * @param authenticationMechanism The method the user used for authenticating to the repository.
 * @param commitHash              The latest commit hash at the access event.
 * @param timestamp               The date and time when the access event occurred.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record VcsAccessLogDTO(Long id, Long userId, String name, String email, String repositoryActionType, String authenticationMechanism, String commitHash,
        ZonedDateTime timestamp) {

    /**
     * Converts an access log entry into its DTO.
     *
     * @param vcsAccessLog the entry to convert
     * @return the DTO, with a null user id for an access by a build agent
     */
    public static VcsAccessLogDTO of(VcsAccessLog vcsAccessLog) {
        // A build agent clone has no user: it authenticates with the token of the build job it is processing, and the
        // agent and job are recorded in the name instead.
        Long userId = vcsAccessLog.getUser() != null ? vcsAccessLog.getUser().getId() : null;
        return new VcsAccessLogDTO(vcsAccessLog.getId(), userId, vcsAccessLog.getName(), vcsAccessLog.getEmail(), vcsAccessLog.getRepositoryActionType().name(),
                vcsAccessLog.getAuthenticationMechanism().name(), vcsAccessLog.getCommitHash(), vcsAccessLog.getTimestamp());
    }
}
