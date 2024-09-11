package de.tum.cit.aet.artemis.web.rest.repository;

/**
 * Determines if a repository action only reads (e.g. get a file from the repo) or updates (e.g. create a new file in the repo).
 */
public enum RepositoryActionType {
    READ, WRITE, RESET
}
