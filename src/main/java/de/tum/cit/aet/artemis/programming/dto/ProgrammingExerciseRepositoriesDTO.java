package de.tum.cit.aet.artemis.programming.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * What a coding agent needs to work in the git repositories of a programming exercise.
 *
 * @param username       the user name for the HTTPS credentials of git: the login of the owner of the token. The password is the token itself
 * @param branch         the branch of the exercise, which is the one to clone and push
 * @param writable       whether a push is allowed now; false once the exercise is visible to students or has participations
 * @param readOnlyReason why a push is not allowed, if it is not
 * @param repositories   the template, solution, tests and auxiliary repositories
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProgrammingExerciseRepositoriesDTO(String username, String branch, boolean writable, String readOnlyReason,
        List<ProgrammingExerciseRepositoryLocationDTO> repositories) {
}
