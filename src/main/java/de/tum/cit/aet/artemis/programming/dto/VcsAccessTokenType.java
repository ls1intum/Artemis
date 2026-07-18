package de.tum.cit.aet.artemis.programming.dto;

/**
 * The kind of VCS access token shown in the user-settings token overview, used to route a revoke request to the correct table.
 */
public enum VcsAccessTokenType {

    /**
     * A token a student owns for their own participation ({@code participation_vcs_access_token}).
     */
    PARTICIPATION,

    /**
     * A repository-scoped token course staff own for one repository ({@code repository_vcs_access_token}).
     */
    REPOSITORY
}
