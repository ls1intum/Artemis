package de.tum.cit.aet.artemis.programming.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.VcsAccessTokenType;

/**
 * A single VCS access token a user owns, shown in the user-settings token overview. Carries only metadata for display and revocation, never the token secret itself.
 *
 * @param id              the id of the token row (used together with {@link #tokenType} to revoke it)
 * @param tokenType       whether this is a participation token (the user's own participation) or a repository-scoped token (staff)
 * @param repositoryType  the repository type for repository-scoped tokens (TEMPLATE, SOLUTION, TESTS, AUXILIARY or USER); {@code null} for participation tokens
 * @param courseId        the id of the course the token's exercise belongs to (so the client can link to the course); {@code null} if it cannot be resolved
 * @param courseTitle     the title of the course the token's exercise belongs to (disambiguates exercises with the same title across courses)
 * @param examId          the id of the exam the token's exercise belongs to, or {@code null} for a regular course exercise (so the client can build the correct exercise link)
 * @param exerciseGroupId the id of the exercise group of an exam exercise, or {@code null} for a regular course exercise
 * @param exerciseId      the id of the programming exercise the token's repository belongs to (so the client can link to the exercise); {@code null} if it cannot be resolved
 * @param exerciseTitle   the title of the programming exercise the token's repository belongs to
 * @param studentLogin    the login of the student whose assignment repository a staff USER token grants access to; {@code null} for all other tokens
 * @param repositoryUri   the canonical URI of the repository the token grants access to, so it is unambiguous which repository is meant
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record VcsAccessTokenOverviewDTO(long id, VcsAccessTokenType tokenType, RepositoryType repositoryType, Long courseId, String courseTitle, Long examId, Long exerciseGroupId,
        Long exerciseId, String exerciseTitle, String studentLogin, String repositoryUri) {

    /**
     * Projection constructor for a participation token (used directly in a JPQL query, avoiding fragile {@code NULL}/enum literals).
     *
     * @param id              the token id
     * @param courseId        the id of the token's course
     * @param courseTitle     the title of the token's course
     * @param examId          the id of the token's exam, or {@code null} for a course exercise
     * @param exerciseGroupId the id of the token's exercise group, or {@code null} for a course exercise
     * @param exerciseId      the id of the token's programming exercise
     * @param exerciseTitle   the title of the token's programming exercise
     * @param repositoryUri   the canonical URI of the participation's repository
     */
    public VcsAccessTokenOverviewDTO(long id, Long courseId, String courseTitle, Long examId, Long exerciseGroupId, Long exerciseId, String exerciseTitle, String repositoryUri) {
        this(id, VcsAccessTokenType.PARTICIPATION, null, courseId, courseTitle, examId, exerciseGroupId, exerciseId, exerciseTitle, null, repositoryUri);
    }

    /**
     * Projection constructor for a repository-scoped token (used directly in a JPQL query).
     *
     * @param id              the token id
     * @param repositoryType  the repository type the token is scoped to
     * @param courseId        the id of the token's course
     * @param courseTitle     the title of the token's course
     * @param examId          the id of the token's exam, or {@code null} for a course exercise
     * @param exerciseGroupId the id of the token's exercise group, or {@code null} for a course exercise
     * @param exerciseId      the id of the token's programming exercise
     * @param exerciseTitle   the title of the token's programming exercise
     * @param studentLogin    the student's login for a USER token, or {@code null} for a base-repository token
     * @param repositoryUri   the canonical URI of the repository the token is scoped to
     */
    public VcsAccessTokenOverviewDTO(long id, RepositoryType repositoryType, Long courseId, String courseTitle, Long examId, Long exerciseGroupId, Long exerciseId,
            String exerciseTitle, String studentLogin, String repositoryUri) {
        this(id, VcsAccessTokenType.REPOSITORY, repositoryType, courseId, courseTitle, examId, exerciseGroupId, exerciseId, exerciseTitle, studentLogin, repositoryUri);
    }
}
