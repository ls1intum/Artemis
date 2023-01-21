package de.tum.in.www1.artemis.service.connectors.localvc;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.exception.LocalVCException;
import de.tum.in.www1.artemis.exception.VersionControlException;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.security.localVC.LocalVCInternalException;
import de.tum.in.www1.artemis.service.UrlService;
import de.tum.in.www1.artemis.service.connectors.AbstractVersionControlService;
import de.tum.in.www1.artemis.service.connectors.ConnectorHealth;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.programming.ProgrammingMessagingService;
import de.tum.in.www1.artemis.service.programming.ProgrammingSubmissionService;
import de.tum.in.www1.artemis.web.rest.util.StringUtil;

@Service
@Profile("localvc")
public class LocalVCService extends AbstractVersionControlService {

    private final Logger log = LoggerFactory.getLogger(LocalVCService.class);

    @Value("${artemis.version-control.url}")
    private URL localVCServerUrl;

    @Value("${artemis.version-control.local-vcs-repo-path}")
    private String localVCPath;

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final ProgrammingSubmissionService programmingSubmissionService;

    private final ProgrammingMessagingService programmingMessagingService;

    public LocalVCService(UrlService urlService, GitService gitService, ApplicationContext applicationContext,
            ProgrammingExerciseStudentParticipationRepository studentParticipationRepository, ProgrammingExerciseRepository programmingExerciseRepository, Environment environment,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, ProgrammingSubmissionService programmingSubmissionService,
            ProgrammingMessagingService programmingMessagingService) {
        super(applicationContext, gitService, urlService, studentParticipationRepository, programmingExerciseRepository, environment);
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.programmingSubmissionService = programmingSubmissionService;
        this.programmingMessagingService = programmingMessagingService;
    }

    @Override
    public void configureRepository(ProgrammingExercise exercise, ProgrammingExerciseStudentParticipation participation, boolean allowAccess) {
        // For Bitbucket and GitLab, users are added to the respective repository to allow them to fetch from there and push to it
        // if the exercise allows for usage of an offline IDE.
        // For local VCS, users are allowed to access the repository by default if they have access to the repository URL.
        // Instead, the LocalVCFetchFilter and LocalVCPushFilter block requests if offline IDE usage is not allowed.
    }

    @Override
    public void addMemberToRepository(VcsRepositoryUrl repositoryUrl, User user) {
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
            String folderName = localVCPath + File.separator + courseShortName + File.separator + projectKey;
            FileUtils.deleteDirectory(new File(folderName));
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
        if (new File(localVCPath + File.separator + courseShortNameStripped + File.separator + projectKeyStripped).exists()) {
            log.warn("Local git project with key {} already exists: {}", projectKey, projectName);
            return true;
        }

        log.debug("Local git project {} does not exist", projectKey);
        return false;
    }

    /**
     * Create a new project
     *
     * @param programmingExercise the programming exercise for which the local git
     *                            Project should be created
     * @throws LocalVCException if the project could not be created
     */
    @Override
    public void createProjectForExercise(ProgrammingExercise programmingExercise) throws LocalVCException {
        String localVCPathResolved = localVCPath;
        String courseShortName = StringUtil.stripIllegalCharacters(programmingExercise.getCourseViaExerciseGroupOrCourseMember().getShortName());
        String projectKey = StringUtil.stripIllegalCharacters(programmingExercise.getProjectKey());

        log.debug("Creating folder for local git project at {}", localVCPathResolved + File.separator + courseShortName + File.separator + projectKey);

        try {
            // Instead of defining a project like would be done for GitLab or Bitbucket,
            // just define a directory that will contain all repositories.
            File localPath = new File(localVCPathResolved + File.separator + courseShortName + File.separator + projectKey);

            if (!localPath.mkdirs()) {
                throw new IOException("Could not create directory " + localPath.getPath());
            }
        }
        catch (Exception e) {
            log.error("Could not create local git project for key {} in course {}", projectKey, courseShortName, e);
            throw new LocalVCException("Error while creating local git project.");
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

        Path localFilePath = localVCUrl.getLocalPath(localVCPath);

        log.debug("Creating local git repo {} in folder {}", repositorySlug, localFilePath);

        try {
            File remoteDir = localFilePath.toFile();

            if (!remoteDir.mkdirs()) {
                throw new IOException("Could not create directory " + remoteDir.getPath());
            }

            // Create a bare local repository with JGit.
            Git git = Git.init().setDirectory(remoteDir).setBare(true).call();
            Repository repository = git.getRepository();
            RefUpdate refUpdate = repository.getRefDatabase().newUpdate(Constants.HEAD, false);
            refUpdate.setForceUpdate(true);
            refUpdate.link("refs/heads/" + defaultBranch);

            git.close();
        }
        catch (GitAPIException | IOException e) {
            log.error("Could not create local git repo {} at location {}", repositorySlug, localFilePath, e);
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
        // The createNewSubmission method below will create a Commit object straight away and hand that to the processNewProgrammingSubmission method in
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

    /**
     * @param commitHash the hash of the commit that leads to a new submission.
     * @param repository the JGit repository this submission belongs to.
     */
    public void createNewSubmission(String commitHash, Repository repository) {

        File repositoryFolderPath = repository.getDirectory();

        LocalVCRepositoryUrl localVCRepositoryUrl = new LocalVCRepositoryUrl(localVCPath, repositoryFolderPath);

        // For pushes to the "tests" repository, no submission is created.
        if (localVCRepositoryUrl.getRepositoryTypeOrUserName().equals(RepositoryType.TESTS.getName())) {
            return;
        }

        List<ProgrammingExercise> exercises = programmingExerciseRepository.findByProjectKey(localVCRepositoryUrl.getProjectKey());
        if (exercises.size() != 1) {
            throw new LocalVCException("No exercise or multiple exercises found for the given project key: " + localVCRepositoryUrl.getProjectKey());
        }

        ProgrammingExercise exercise = exercises.get(0);

        // Retrieve participation for the repository.
        ProgrammingExerciseParticipation participation;

        if (localVCRepositoryUrl.getRepositoryTypeOrUserName().equals(RepositoryType.TEMPLATE.getName())) {
            participation = templateProgrammingExerciseParticipationRepository.findWithEagerResultsAndSubmissionsByProgrammingExerciseId(exercise.getId()).orElse(null);
        }
        else if (localVCRepositoryUrl.getRepositoryTypeOrUserName().equals(RepositoryType.SOLUTION.getName())) {
            participation = solutionProgrammingExerciseParticipationRepository.findWithEagerResultsAndSubmissionsByProgrammingExerciseId(exercise.getId()).orElse(null);
        }
        else {
            List<ProgrammingExerciseStudentParticipation> participations = programmingExerciseStudentParticipationRepository
                    .findWithSubmissionsByExerciseIdAndStudentLogin(exercise.getId(), localVCRepositoryUrl.getRepositoryTypeOrUserName());
            if (participations.size() != 1) {
                participation = null;
            }
            else {
                participation = participations.get(0);
            }
        }

        if (participation == null) {
            throw new LocalVCInternalException("No participation found for repository " + repository.getDirectory().getPath());
        }

        Commit commit = extractCommitInfo(commitHash, repository);

        try {
            // The 'user' is not properly logged into Artemis, this leads to an issue when accessing custom repository methods.
            // Therefore, a mock auth object has to be created.
            SecurityUtils.setAuthorizationObject();
            ProgrammingSubmission submission = programmingSubmissionService.processNewProgrammingSubmission(participation, commit);
            // Remove unnecessary information from the new submission.
            submission.getParticipation().setSubmissions(null);
            programmingMessagingService.notifyUserAboutSubmission(submission);
        }
        catch (Exception ex) {
            log.error("Exception encountered when trying to create a new submission for participation {} with the following commit: {}", participation.getId(), commit, ex);
            throw new LocalVCInternalException();
        }
    }

    private Commit extractCommitInfo(String commitHash, Repository repository) {
        RevCommit revCommit;
        String branch;

        try {
            ObjectId objectId = repository.resolve(commitHash);
            revCommit = repository.parseCommit(objectId);
            branch = repository.getBranch();
        }
        catch (Exception e) {
            throw new LocalVCInternalException(e.getMessage());
        }

        if (revCommit == null || branch == null) {
            throw new LocalVCInternalException();
        }

        Commit commit = new Commit();
        commit.setCommitHash(commitHash);
        commit.setAuthorName(revCommit.getAuthorIdent().getName());
        commit.setAuthorEmail(revCommit.getAuthorIdent().getEmailAddress());
        commit.setBranch(branch);
        commit.setMessage(revCommit.getFullMessage());

        return commit;
    }
}
