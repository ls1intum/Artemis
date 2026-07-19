package de.tum.cit.aet.artemis.programming.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.VcsAccessTokenType;

/**
 * A single VCS access token a user owns, shown in the user-settings token overview. Carries only metadata for display and revocation, never the token secret itself.
 *
 * @param id             the id of the token row (used together with {@link #tokenType} to revoke it)
 * @param tokenType      whether this is a participation token (the user's own participation) or a repository-scoped token (staff)
 * @param repositoryType the repository type for repository-scoped tokens (TEMPLATE, SOLUTION, TESTS, AUXILIARY or USER); {@code null} for participation tokens
 * @param courseTitle    the title of the course the token's exercise belongs to (disambiguates exercises with the same title across courses)
 * @param exerciseTitle  the title of the programming exercise the token's repository belongs to
 * @param studentLogin   the login of the student whose assignment repository a staff USER token grants access to; {@code null} for all other tokens
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record VcsAccessTokenOverviewDTO(long id, VcsAccessTokenType tokenType, RepositoryType repositoryType, String courseTitle, String exerciseTitle, String studentLogin) {

    /**
     * Projection constructor for a participation token (used directly in a JPQL query, avoiding fragile {@code NULL}/enum literals).
     *
     * @param id            the token id
     * @param courseTitle   the title of the token's course
     * @param exerciseTitle the title of the token's programming exercise
     */
    public VcsAccessTokenOverviewDTO(long id, String courseTitle, String exerciseTitle) {
        this(id, VcsAccessTokenType.PARTICIPATION, null, courseTitle, exerciseTitle, null);
    }

    /**
     * Projection constructor for a repository-scoped token (used directly in a JPQL query).
     *
     * @param id             the token id
     * @param repositoryType the repository type the token is scoped to
     * @param courseTitle    the title of the token's course
     * @param exerciseTitle  the title of the token's programming exercise
     * @param studentLogin   the student's login for a USER token, or {@code null} for a base-repository token
     */
    public VcsAccessTokenOverviewDTO(long id, RepositoryType repositoryType, String courseTitle, String exerciseTitle, String studentLogin) {
        this(id, VcsAccessTokenType.REPOSITORY, repositoryType, courseTitle, exerciseTitle, studentLogin);
    }
}
