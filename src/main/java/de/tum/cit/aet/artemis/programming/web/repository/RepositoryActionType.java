package de.tum.cit.aet.artemis.programming.web.repository;

/**
 * Determines if a repository action only reads (e.g. get a file from the repo) or updates (e.g. create a new file in the repo).
 */
public enum RepositoryActionType {
    READ, WRITE, RESET
}
