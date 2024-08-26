package de.tum.in.www1.artemis.web.rest.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.vcstokens.VcsAccessLog;

/**
 * DTO representing a VCS access log entry.
 *
 * @param id                      The id of the access log entry.
 * @param userId                  The user's id associated with the access log event.
 * @param name                    The name associated with the user.
 * @param email                   The email associated with the user.
 * @param repositoryActionType    The type of action performed in the repository (read or write).
 * @param authenticationMechanism The method the user used for authenticating to the repository.
 * @param commitHash              The latest commit hash at the access event.
 * @param timestamp               The date and time when the access event occurred.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record VcsAccessLogDTO(Long id, Long userId, String name, String email, String repositoryActionType, String authenticationMechanism, String commitHash,
        ZonedDateTime timestamp) {

    public static VcsAccessLogDTO of(VcsAccessLog vcsAccessLog) {
        return new VcsAccessLogDTO(vcsAccessLog.getId(), vcsAccessLog.getUser().getId(), vcsAccessLog.getName(), vcsAccessLog.getEmail(),
                vcsAccessLog.getRepositoryActionType().name(), vcsAccessLog.getAuthenticationMechanism().name(), vcsAccessLog.getCommitHash(), vcsAccessLog.getTimestamp());
    }
}
