package de.tum.in.www1.artemis.service.connectors.localvc;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.exception.LocalVCException;
import de.tum.in.www1.artemis.exception.VersionControlException;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.UrlService;
import de.tum.in.www1.artemis.service.connectors.AbstractVersionControlService;
import de.tum.in.www1.artemis.service.connectors.ConnectorHealth;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.web.rest.util.StringUtil;

@Service
@Profile("localvc")
public class LocalVCService extends AbstractVersionControlService {

    private final Logger log = LoggerFactory.getLogger(LocalVCService.class);

    @Value("${artemis.version-control.url}")
    private URL localVCServerUrl;

    @Value("${artemis.version-control.local-vcs-repo-path}")
    private String localVCPath;

    public LocalVCService(UrlService urlService, GitService gitService, ApplicationContext applicationContext,
            ProgrammingExerciseStudentParticipationRepository studentParticipationRepository, ProgrammingExerciseRepository programmingExerciseRepository) {
        super(applicationContext, gitService, urlService, studentParticipationRepository, programmingExerciseRepository);
    }

    @Override
    public void configureRepository(ProgrammingExercise exercise, ProgrammingExerciseStudentParticipation participation, boolean allowAccess) {
        // For Bitbucket and GitLab, users are added to the respective repository to allow them to fetch from there and push to it
        // if the exercise allows for usage of an offline IDE.
        // For local VCS, users are allowed to access the repository by default if they have access to the repository URL.
        // Instead, the LocalVCFetchFilter and LocalVCPushFilter block requests if offline IDE usage is not allowed.
    }

    @Override
    public void addMemberToRepository(VcsRepositoryUrl repositoryUrl, User user, RepositoryPermissions permissions) {
        // Members cannot be added to a local repository. Authenticated users have access by default and are authorized in the LocalVCFetchFilter and LocalVCPushFilter.
    }

    @Override
    public void removeMemberFromRepository(VcsRepositoryUrl repositoryUrl, User user) {
        // Members cannot be removed from a local repository.
        // Authorization is checked in the LocalVCFetchFilter and LocalVCPushFilter.
    }

    @Override
    protected void addWebHook(VcsRepositoryUrl repositoryUrl, String notificationUrl, String webHookName) {
        // Webhooks must not be added for the local git server. The LocalVCPostPushHook notifies Artemis on every push.
    }

    @Override
    protected void addAuthenticatedWebHook(VcsRepositoryUrl repositoryUrl, String notificationUrl, String webHookName, String secretToken) {
        // Not needed for local VC.
        throw new UnsupportedOperationException("Authenticated webhooks with local VC are not supported!");
    }

    @Override
    public void deleteProject(String courseShortName, String projectKey) {
        try {
            Path projectPath = Path.of(localVCPath, courseShortName, projectKey);
            FileUtils.deleteDirectory(projectPath.toFile());
        }
        catch (IOException e) {
            log.error("Could not delete project", e);
        }
    }

    @Override
    public void deleteRepository(VcsRepositoryUrl repositoryUrl) {
        try {
            LocalVCRepositoryUrl localVCRepositoryUrl = new LocalVCRepositoryUrl(localVCServerUrl, repositoryUrl.toString());
            Path localPath = localVCRepositoryUrl.getLocalPath(localVCPath);
            FileUtils.deleteDirectory(localPath.toFile());
        }
        catch (IOException e) {
            log.error("Could not delete repository", e);
        }
    }

    @Override
    public VcsRepositoryUrl getCloneRepositoryUrl(String projectKey, String courseShortName, String repositorySlug) {
        return new LocalVCRepositoryUrl(localVCServerUrl, projectKey, courseShortName, repositorySlug);
    }

    @Override
    public void setRepositoryPermissionsToReadOnly(VcsRepositoryUrl repositoryUrl, String projectKey, Set<User> users) throws LocalVCException {
        // Not implemented for local VC. All checks for whether a student can access a repository are conducted in the LocalVCFetchFilter and LocalVCPushFilter.
    }

    /**
     * Get the default branch of the repository
     *
     * @param repositoryUrl The repository url to get the default branch for.
     * @return the name of the default branch, e.g. 'main'
     */
    @Override
    public String getDefaultBranchOfRepository(VcsRepositoryUrl repositoryUrl) throws LocalVCException {
        try {
            LocalVCRepositoryUrl localVCRepositoryUrl = new LocalVCRepositoryUrl(localVCServerUrl, repositoryUrl.toString());
            String localRepositoryPath = localVCRepositoryUrl.getLocalPath(localVCPath).toString();
            Map<String, Ref> remoteRepositoryRefs = Git.lsRemoteRepository().setRemote(localRepositoryPath).callAsMap();
            if (remoteRepositoryRefs.containsKey("HEAD")) {
                return remoteRepositoryRefs.get("HEAD").getTarget().getName();
            }

            throw new LocalVCException("Cannot get default branch of repository " + localRepositoryPath + ". ls-remote does not return a HEAD reference.");
        }
        catch (Exception e) {
            log.error("Unable to get default branch for repository {}", repositoryUrl.folderNameForRepositoryUrl(), e);
            throw new LocalVCException("Unable to get default branch for repository " + repositoryUrl.folderNameForRepositoryUrl(), e);
        }
    }

    @Override
    public void unprotectBranch(VcsRepositoryUrl repositoryUrl, String branch) throws VersionControlException {
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
    public boolean checkIfProjectExists(String projectKey, String courseShortName, String projectName) {
        String projectKeyStripped = StringUtil.stripIllegalCharacters(projectKey);
        String courseShortNameStripped = StringUtil.stripIllegalCharacters(courseShortName);

        // Try to find the folder in the file system. If it is not found, return false.
        Path projectPath = Path.of(localVCPath, courseShortNameStripped, projectKeyStripped);
        if (Files.exists(projectPath)) {
            log.warn("Local VC project with key {} already exists: {}", projectKey, projectName);
            return true;
        }

        log.debug("Local git project {} does not exist", projectKey);
        return false;
    }

    /**
     * Create a new project
     *
     * @param programmingExercise the programming exercise for which the local git
     *                                Project should be created
     * @throws LocalVCException if the project could not be created
     */
    @Override
    public void createProjectForExercise(ProgrammingExercise programmingExercise) throws LocalVCException {
        String courseShortName = StringUtil.stripIllegalCharacters(programmingExercise.getCourseViaExerciseGroupOrCourseMember().getShortName());
        String projectKey = StringUtil.stripIllegalCharacters(programmingExercise.getProjectKey());

        log.debug("Creating folder for local git project at {}", Path.of(localVCPath, courseShortName, projectKey));

        try {
            // Instead of defining a project like would be done for GitLab or Bitbucket, just define a directory that will contain all repositories.
            Path projectPath = Path.of(localVCPath, courseShortName, projectKey);
            Files.createDirectories(projectPath);
        }
        catch (IOException e) {
            log.error("Could not create local git project for key {} in course {}", projectKey, courseShortName, e);
            throw new LocalVCException("Error while creating local VC project.");
        }
    }

    @Override
    public ConnectorHealth health() {
        ConnectorHealth health = new ConnectorHealth(true);
        health.setAdditionalInfo(Map.of("url", localVCServerUrl));
        return health;
    }

    @Override
    public void createRepository(String projectKey, String courseShortName, String repositorySlug, String parentProjectKey) {
        createRepository(projectKey, courseShortName, repositorySlug);
    }

    /**
     * Create a new repo
     *
     * @param projectKey      The project key of the parent project
     * @param courseShortName The short name of the course the repository belongs to
     * @param repositorySlug  The name for the new repository
     * @throws LocalVCException if the repo could not be created
     */
    private void createRepository(String projectKey, String courseShortName, String repositorySlug) throws LocalVCException {

        LocalVCRepositoryUrl localVCUrl = new LocalVCRepositoryUrl(localVCServerUrl, projectKey, courseShortName, repositorySlug);

        Path remoteDirPath = localVCUrl.getLocalPath(localVCPath);

        log.debug("Creating local git repository {} in folder {}", repositorySlug, remoteDirPath);

        try {
            Files.createDirectories(remoteDirPath);

            // Create a bare local repository with JGit.
            Git git = Git.init().setDirectory(remoteDirPath.toFile()).setBare(true).call();
            Repository repository = git.getRepository();
            RefUpdate refUpdate = repository.getRefDatabase().newUpdate(Constants.HEAD, false);
            refUpdate.setForceUpdate(true);
            refUpdate.link("refs/heads/" + defaultBranch);

            git.close();
        }
        catch (GitAPIException | IOException e) {
            log.error("Could not create local git repo {} at location {}", repositorySlug, remoteDirPath, e);
            throw new LocalVCException("Error while creating local git project.");
        }
    }

    @Override
    public Boolean repositoryUrlIsValid(@Nullable VcsRepositoryUrl repositoryUrl) {
        if (repositoryUrl == null || repositoryUrl.getURI() == null) {
            return false;
        }
        try {
            new LocalVCRepositoryUrl(localVCServerUrl, repositoryUrl.toString());
        }
        catch (LocalVCException e) {
            return false;
        }
        return true;
    }

    @Override
    @NotNull
    public Commit getLastCommitDetails(Object requestBody) throws LocalVCException {
        // The local VCS will create a Commit object straight away and hand that to the processNewProgrammingSubmission method in
        // ProgrammingSubmissionService.
        return (Commit) requestBody;
    }

    @Override
    public ZonedDateTime getPushDate(ProgrammingExerciseParticipation participation, String commitHash, Object eventObject) throws LocalVCException {
        // The local CIS will provide an eventObject that contains the push date.
        JsonNode node = new ObjectMapper().convertValue(eventObject, JsonNode.class);
        String dateString = node.get("date").asText(null);
        try {
            return ZonedDateTime.parse(dateString, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ"));
        }
        catch (DateTimeParseException e) {
            throw new LocalVCException("Unable to get the push date from participation.");
        }
    }
}
