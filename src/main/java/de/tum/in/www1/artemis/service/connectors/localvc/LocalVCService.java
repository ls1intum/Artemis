package de.tum.in.www1.artemis.service.connectors.localvc;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Commit;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.exception.localvc.LocalVCInternalException;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.service.UrlService;
import de.tum.in.www1.artemis.service.connectors.ConnectorHealth;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.vcs.AbstractVersionControlService;
import de.tum.in.www1.artemis.service.connectors.vcs.VersionControlRepositoryPermission;

/**
 * Implementation of VersionControlService for the local VC server.
 */
@Service
@Profile("localvc")
public class LocalVCService extends AbstractVersionControlService {

    private final Logger log = LoggerFactory.getLogger(LocalVCService.class);

    @Value("${artemis.version-control.url}")
    private URL localVCBaseUrl;

    @Value("${artemis.version-control.local-vcs-repo-path}")
    private String localVCBasePath;

    public LocalVCService(UrlService urlService, GitService gitService, ApplicationContext applicationContext,
            ProgrammingExerciseStudentParticipationRepository studentParticipationRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository) {
        super(applicationContext, gitService, urlService, studentParticipationRepository, programmingExerciseRepository, templateProgrammingExerciseParticipationRepository);
    }

    @Override
    public void configureRepository(ProgrammingExercise exercise, ProgrammingExerciseStudentParticipation participation, boolean allowAccess) {
        // For Bitbucket and GitLab, users are added to the respective repository to allow them to fetch from there and push to it
        // if the exercise allows for usage of an offline IDE.
        // For local VCS, users are allowed to access the repository by default if they have access to the repository URL.
        // Instead, the LocalVCFetchFilter and LocalVCPushFilter block requests if offline IDE usage is not allowed.
    }

    @Override
    public void addMemberToRepository(VcsRepositoryUrl repositoryUrl, User user, VersionControlRepositoryPermission permissions) {
        // Members cannot be added to a local repository. Authenticated users have access by default and are authorized in the LocalVCFetchFilter and LocalVCPushFilter.
    }

    @Override
    public void removeMemberFromRepository(VcsRepositoryUrl repositoryUrl, User user) {
        // Members cannot be removed from a local repository.
        // Authorization is checked in the LocalVCFetchFilter and LocalVCPushFilter.
    }

    @Override
    protected void addWebHook(VcsRepositoryUrl repositoryUrl, String notificationUrl, String webHookName) {
        // Webhooks must not be added for the local VC system. The LocalVCPostPushHook notifies Artemis on every push.
    }

    @Override
    protected void addAuthenticatedWebHook(VcsRepositoryUrl repositoryUrl, String notificationUrl, String webHookName, String secretToken) {
        // Webhooks must not be added for the local VC system. The LocalVCPostPushHook notifies Artemis on every push.
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
            Path projectPath = Path.of(localVCBasePath, projectKey);
            FileUtils.deleteDirectory(projectPath.toFile());
        }
        catch (IOException e) {
            throw new LocalVCInternalException("Could not delete project " + projectKey, e);
        }
    }

    /**
     * Delete the repository at the given repository URL
     *
     * @param repositoryUrl of the repository that should be deleted
     * @throws LocalVCInternalException if the repository cannot be deleted
     */
    @Override
    public void deleteRepository(VcsRepositoryUrl repositoryUrl) {

        LocalVCRepositoryUrl localVCRepositoryUrl = new LocalVCRepositoryUrl(repositoryUrl.toString(), localVCBaseUrl);
        Path localRepositoryPath = localVCRepositoryUrl.getLocalRepositoryPath(localVCBasePath);

        try {
            FileUtils.deleteDirectory(localRepositoryPath.toFile());
        }
        catch (IOException e) {
            throw new LocalVCInternalException("Could not delete repository at " + localRepositoryPath, e);
        }
    }

    /**
     * Get the VcsRepositoryUrl for the given project key and repository slug
     *
     * @param projectKey     The project key
     * @param repositorySlug The repository slug
     * @return The VcsRepositoryUrl
     * @throws LocalVCInternalException if the repository URL cannot be constructed
     */
    @Override
    public VcsRepositoryUrl getCloneRepositoryUrl(String projectKey, String repositorySlug) {
        return new LocalVCRepositoryUrl(projectKey, repositorySlug, localVCBaseUrl);
    }

    @Override
    public void setRepositoryPermissionsToReadOnly(VcsRepositoryUrl repositoryUrl, String projectKey, Set<User> users) {
        // Not implemented for local VC. All checks for whether a student can access a repository are conducted in the LocalVCFetchFilter and LocalVCPushFilter.
    }

    /**
     * Get the default branch of the repository
     *
     * @param repositoryUrl The repository url to get the default branch for.
     * @return the name of the default branch, e.g. 'main'
     * @throws LocalVCInternalException if the default branch cannot be determined
     */
    @Override
    public String getDefaultBranchOfRepository(VcsRepositoryUrl repositoryUrl) {
        LocalVCRepositoryUrl localVCRepositoryUrl = new LocalVCRepositoryUrl(repositoryUrl.toString(), localVCBaseUrl);
        String localRepositoryPath = localVCRepositoryUrl.getLocalRepositoryPath(localVCBasePath).toString();
        Map<String, Ref> remoteRepositoryRefs;
        try {
            remoteRepositoryRefs = Git.lsRemoteRepository().setRemote(localRepositoryPath).callAsMap();
        }
        catch (GitAPIException e) {
            throw new LocalVCInternalException("Cannot get default branch of repository " + localRepositoryPath + ". ls-remote failed.", e);
        }
        if (remoteRepositoryRefs.containsKey("HEAD")) {
            // The HEAD reference is of the form "ref: refs/heads/main"
            String[] headRefSplit = remoteRepositoryRefs.get("HEAD").getTarget().getName().split("/");
            return headRefSplit[headRefSplit.length - 1];
        }

        throw new LocalVCInternalException("Cannot get default branch of repository " + localRepositoryPath + ". ls-remote does not return a HEAD reference.");
    }

    @Override
    public void unprotectBranch(VcsRepositoryUrl repositoryUrl, String branch) {
        // Not implemented. It is not needed for local VC for the current use
        // case, because the main branch is unprotected by default.
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
        Path projectPath = Path.of(localVCBasePath, projectKey);
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
            // Instead of defining a project like would be done for GitLab or Bitbucket, just create a directory that will contain all repositories.
            Path projectPath = Path.of(localVCBasePath, projectKey);
            Files.createDirectories(projectPath);
            log.debug("Created folder for local git project at {}", projectPath);
        }
        catch (IOException e) {
            throw new LocalVCInternalException("Error while creating local VC project.", e);
        }
    }

    @Override
    public ConnectorHealth health() {
        ConnectorHealth health = new ConnectorHealth(true);
        health.setAdditionalInfo(Map.of("url", localVCBaseUrl));
        return health;
    }

    /**
     * Create a new repository for the given project key and repository slug
     *
     * @param projectKey     The project key of the parent project
     * @param repositorySlug The name for the new repository
     * @throws LocalVCInternalException if the repository could not be created
     */
    @Override
    public void createRepository(String projectKey, String repositorySlug, String parentProjectKey) {
        createRepository(projectKey, repositorySlug);
    }

    private void createRepository(String projectKey, String repositorySlug) {

        LocalVCRepositoryUrl localVCRepositoryUrl = new LocalVCRepositoryUrl(projectKey, repositorySlug, localVCBaseUrl);

        Path remoteDirPath = localVCRepositoryUrl.getLocalRepositoryPath(localVCBasePath);

        try {
            Files.createDirectories(remoteDirPath);

            // Create a bare local repository with JGit.
            Git git = Git.init().setDirectory(remoteDirPath.toFile()).setBare(true).call();

            // Change the default branch to the Artemis default branch.
            Repository repository = git.getRepository();
            RefUpdate refUpdate = repository.getRefDatabase().newUpdate(Constants.HEAD, false);
            refUpdate.setForceUpdate(true);
            refUpdate.link("refs/heads/" + defaultBranch);

            git.close();
            log.debug("Created local git repository {} in folder {}", repositorySlug, remoteDirPath);
        }
        catch (GitAPIException | IOException e) {
            log.error("Could not create local git repo {} at location {}", repositorySlug, remoteDirPath, e);
            throw new LocalVCInternalException("Error while creating local git project.", e);
        }
    }

    @Override
    public Boolean repositoryUrlIsValid(@Nullable VcsRepositoryUrl repositoryUrl) {
        if (repositoryUrl == null || repositoryUrl.getURI() == null) {
            return false;
        }

        try {
            new LocalVCRepositoryUrl(repositoryUrl.toString(), localVCBaseUrl);
        }
        catch (LocalVCInternalException e) {
            return false;
        }

        return true;
    }

    @Override
    @NotNull
    public Commit getLastCommitDetails(Object requestBody) {
        // The local VCS will create a Commit object straight away and hand that to the processNewProgrammingSubmission method in
        // ProgrammingSubmissionService.
        return (Commit) requestBody;
    }

    /**
     * Get the date of a push event. If the event object is supplied we try to retrieve the push date from there.
     * Otherwise, we use the participation to retrieve the repository and use the commitHash to determine the date of the latest commit.
     *
     * @param participation The participation we retrieve the repository for.
     * @param commitHash    The commit hash that identifies the latest commit.
     * @param eventObject   An object describing the push event, that contains the node "date". null if not available
     * @return The date of the push event or the date of the latest commit.
     * @throws LocalVCInternalException if the repository could not be retrieved or the push date could not be retrieved from the repository.
     */
    @Override
    public ZonedDateTime getPushDate(ProgrammingExerciseParticipation participation, String commitHash, Object eventObject) {
        // The eventObject is null for every call of this method. Use the commitHash to determine date of the latest commit.

        de.tum.in.www1.artemis.domain.Repository repository;
        try {
            repository = gitService.getOrCheckoutRepository(participation);
        }
        catch (GitAPIException e) {
            throw new LocalVCInternalException("Unable to get the repository from participation " + participation.getId() + ": " + participation.getRepositoryUrl(), e);
        }

        try (RevWalk revWalk = new RevWalk(repository)) {
            RevCommit commit = revWalk.parseCommit(repository.resolve(commitHash));

            // Convert the commit time to a ZonedDateTime using the system default time zone.
            Instant instant = Instant.ofEpochSecond(commit.getCommitTime());
            ZoneId zoneId = ZoneId.systemDefault();
            return ZonedDateTime.ofInstant(instant, zoneId);
        }
        catch (IOException e) {
            throw new LocalVCInternalException("Unable to get the push date from participation " + participation.getId() + ": " + participation.getRepositoryUrl(), e);
        }

    }
}
