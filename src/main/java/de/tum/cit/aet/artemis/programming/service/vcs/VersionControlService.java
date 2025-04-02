package de.tum.cit.aet.artemis.programming.service.vcs;

import jakarta.annotation.Nullable;

import de.tum.cit.aet.artemis.core.exception.VersionControlException;
import de.tum.cit.aet.artemis.core.service.connectors.ConnectorHealth;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;

public interface VersionControlService {

    /**
     * Deletes the project for the given project key
     *
     * @param projectKey of the project that should be deleted
     */
    void deleteProject(String projectKey);

    /**
     * Deletes the repository at the given uri
     *
     * @param repositoryUri of the repository that should be deleted
     */
    void deleteRepository(VcsRepositoryUri repositoryUri);

    /**
     * Get the clone URL used for cloning
     *
     * @param projectKey     The project key
     * @param repositorySlug The repository slug
     * @return The clone URL
     */
    VcsRepositoryUri getCloneRepositoryUri(String projectKey, String repositorySlug);

    /**
     * Check if the given repository uri is valid and accessible.
     *
     * @param repositoryUri the VCS repository URI
     * @return whether the repository is valid
     */
    Boolean repositoryUriIsValid(@Nullable VcsRepositoryUri repositoryUri);

    /**
     * Creates a project on the VCS.
     *
     * @param programmingExercise for which a project should be created
     * @throws VersionControlException if the project could not be created
     */
    void createProjectForExercise(ProgrammingExercise programmingExercise) throws VersionControlException;

    /**
     * Creates a repository on the VCS.
     *
     * @param projectKey The key of the project that contains the repository (must exist)
     * @param repoName   The name of the repository
     * @throws VersionControlException if the repository could not be created
     */
    void createRepository(String projectKey, String repoName) throws VersionControlException;

    /**
     * Checks if the project with the given projectKey already exists
     *
     * @param projectKey  to check if a project with this unique key already exists
     * @param projectName to check if a project with the same name already exists
     * @return true if the project exists, false otherwise
     */
    boolean checkIfProjectExists(String projectKey, String projectName);

    /**
     * Copies a repository from one project to another one. The project can be the same.
     *
     * @param sourceProjectKey     The key of the template project (normally based on the course and exercise short name)
     * @param sourceRepositoryName The name of the repository which should be copied
     * @param sourceBranch         The default branch of the source repository
     * @param targetProjectKey     The key of the target project to which to copy the new repository to
     * @param targetRepositoryName The desired name of the target repository
     * @param attempt              The attempt number
     * @return The URL for cloning the repository
     * @throws VersionControlException if the repository could not be copied on the VCS server (e.g. because the source repo does not exist)
     */
    VcsRepositoryUri copyRepository(String sourceProjectKey, String sourceRepositoryName, String sourceBranch, String targetProjectKey, String targetRepositoryName,
            Integer attempt) throws VersionControlException;

    /**
     * Get the default branch of the repository
     *
     * @param repositoryUri The repository uri to get the default branch for.
     * @return the name of the default branch, e.g. 'main'
     */
    String getDefaultBranchOfRepository(VcsRepositoryUri repositoryUri) throws VersionControlException;

    /**
     * Checks if the underlying VCS server is up and running and gives some additional information about the running
     * services if available
     *
     * @return The health of the VCS service containing if it is up and running and any additional data, or the throwing exception otherwise
     */
    ConnectorHealth health();

    /**
     * Get the default branch used in the participation or retrieves it from the VCS if not present in the database
     *
     * @param participation The participation to get the default branch from
     * @return The default branch used by this participation
     */
    String getOrRetrieveBranchOfParticipation(ProgrammingExerciseParticipation participation);

    /**
     * Get the default branch used in the programmingExercise or retrieves it from the VCS if not present in the database
     *
     * @param programmingExercise The participation to get the default branch from
     * @return The default branch used by this programmingExercise
     */
    String getOrRetrieveBranchOfExercise(ProgrammingExercise programmingExercise);
}
