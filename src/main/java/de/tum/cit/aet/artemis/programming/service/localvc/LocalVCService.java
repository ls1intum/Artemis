package de.tum.cit.aet.artemis.programming.service.localvc;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.localvc.LocalVCInternalException;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.UriService;
import de.tum.cit.aet.artemis.programming.service.vcs.AbstractVersionControlService;

/**
 * Implementation of VersionControlService for the local VC server.
 */
@Lazy
@Service
@Profile(PROFILE_LOCALVC)
public class LocalVCService extends AbstractVersionControlService {

    private static final Logger log = LoggerFactory.getLogger(LocalVCService.class);

    @Value("${artemis.version-control.default-branch:main}")
    protected String defaultBranch;

    @Value("${artemis.version-control.url}")
    private URI localVCBaseUri;

    @Value("${artemis.version-control.local-vcs-repo-path}")
    private Path localVCBasePath;

    public LocalVCService(UriService uriService, GitService gitService, ProgrammingExerciseStudentParticipationRepository studentParticipationRepository,
            ProgrammingExerciseRepository programmingExerciseRepository, TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository) {
        super(gitService, uriService, studentParticipationRepository, programmingExerciseRepository, templateProgrammingExerciseParticipationRepository,
                programmingExerciseBuildConfigRepository);
    }

    /**
     * Delete the entire project including all repositories for a given project key.
     *
     * @param projectKey of the project that should be deleted.
     * @throws LocalVCInternalException if the project cannot be deleted.
     */
    @Override
    public void deleteProject(String projectKey) {
        try {
            Path projectPath = localVCBasePath.resolve(projectKey);
            FileUtils.deleteDirectory(projectPath.toFile());
        }
        catch (IOException e) {
            throw new LocalVCInternalException("Could not delete project " + projectKey, e);
        }
    }

    /**
     * Delete the repository at the given repository URI
     *
     * @param localVCRepositoryUri of the repository that should be deleted
     * @throws LocalVCInternalException if the repository cannot be deleted
     */
    @Override
    public void deleteRepository(LocalVCRepositoryUri localVCRepositoryUri) {

        Path localRepositoryPath = localVCRepositoryUri.getLocalRepositoryPath(localVCBasePath);

        try {
            FileUtils.deleteDirectory(localRepositoryPath.toFile());
        }
        catch (IOException e) {
            throw new LocalVCInternalException("Could not delete repository at " + localRepositoryPath, e);
        }
    }

    /**
     * Get the LocalVCRepositoryUri for the given project key and repository slug
     *
     * @param projectKey     The project key
     * @param repositorySlug The repository slug
     * @return The LocalVCRepositoryUri
     * @throws LocalVCInternalException if the repository URI cannot be constructed
     */
    @Override
    public LocalVCRepositoryUri getCloneRepositoryUri(String projectKey, String repositorySlug) {
        return new LocalVCRepositoryUri(localVCBaseUri, projectKey, repositorySlug);
    }

    /**
     * Check if a project already exists in the file system to make sure the new projectKey is unique.
     *
     * @param projectKey  to check if a project with this unique key already exists.
     * @param projectName to check if a project with the same name already exists.
     * @return true or false depending on whether the respective folder exists.
     */
    @Override
    public boolean checkIfProjectExists(String projectKey, String projectName) {
        // Try to find the folder in the file system. If it is not found, return false.
        Path projectPath = localVCBasePath.resolve(projectKey);
        return Files.exists(projectPath);
    }

    /**
     * Create a new project
     *
     * @param programmingExercise the programming exercise for which the local git
     *                                Project should be created
     * @throws LocalVCInternalException if the project could not be created
     */
    @Override
    public void createProjectForExercise(ProgrammingExercise programmingExercise) {
        String projectKey = programmingExercise.getProjectKey();
        try {
            // Create a directory that will contain all repositories.
            Path projectPath = localVCBasePath.resolve(projectKey);
            Files.createDirectories(projectPath);
            log.debug("Created folder for local git project at {}", projectPath);
        }
        catch (IOException e) {
            throw new LocalVCInternalException("Error while creating local VC project.", e);
        }
    }

    /**
     * Create a new repository for the given project key and repository slug
     *
     * @param projectKey     The project key of the parent project
     * @param repositorySlug The name for the new repository
     * @throws LocalVCInternalException if the repository could not be created
     */
    @Override
    public void createRepository(String projectKey, String repositorySlug) {
        LocalVCRepositoryUri localVCRepositoryUri = new LocalVCRepositoryUri(localVCBaseUri, projectKey, repositorySlug);

        Path remoteDirPath = localVCRepositoryUri.getLocalRepositoryPath(localVCBasePath);

        try {
            Files.createDirectories(remoteDirPath);

            // Create a bare local repository with JGit.
            try (Git git = Git.init().setDirectory(remoteDirPath.toFile()).setBare(true).call()) {
                // Change the default branch to the Artemis default branch.
                Repository repository = git.getRepository();
                RefUpdate refUpdate = repository.getRefDatabase().newUpdate(Constants.HEAD, false);
                refUpdate.setForceUpdate(true);
                refUpdate.link("refs/heads/" + defaultBranch);
            }
            log.debug("Created local git repository {} in folder {}", repositorySlug, remoteDirPath);
        }
        catch (GitAPIException | IOException e) {
            log.error("Could not create local git repo {} at location {}", repositorySlug, remoteDirPath, e);
            throw new LocalVCInternalException("Error while creating local git project.", e);
        }
    }

    @Override
    public boolean repositoryUriIsValid(@Nullable LocalVCRepositoryUri repositoryUri) {
        if (repositoryUri == null || repositoryUri.getURI() == null) {
            return false;
        }

        try {
            new LocalVCRepositoryUri(repositoryUri.toString());
        }
        catch (LocalVCInternalException e) {
            return false;
        }

        return true;
    }
}
