package de.tum.in.www1.artemis.service.connectors.gitlab;

import static org.gitlab4j.api.models.AccessLevel.*;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.apache.http.HttpStatus;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.Commit;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.VersionControlException;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.UrlService;
import de.tum.in.www1.artemis.service.connectors.AbstractVersionControlService;
import de.tum.in.www1.artemis.service.connectors.ConnectorHealth;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.gitlab.dto.GitLabPushNotificationDTO;
import de.tum.in.www1.artemis.service.util.UrlUtils;

@Profile("gitlab")
@Service
public class GitLabService extends AbstractVersionControlService {

    private final Logger log = LoggerFactory.getLogger(GitLabService.class);

    @Value("${artemis.version-control.url}")
    private URL gitlabServerUrl;

    @Value("${artemis.version-control.ci-token}")
    private String ciToken;

    private final UserRepository userRepository;

    private final RestTemplate shortTimeoutRestTemplate;

    private final GitLabUserManagementService gitLabUserManagementService;

    private final GitLabApi gitlab;

    private final ScheduledExecutorService scheduler;

    public GitLabService(UserRepository userRepository, UrlService urlService, @Qualifier("shortTimeoutGitlabRestTemplate") RestTemplate shortTimeoutRestTemplate, GitLabApi gitlab,
            GitLabUserManagementService gitLabUserManagementService, GitService gitService, ApplicationContext applicationContext) {
        super(applicationContext, urlService, gitService);
        this.userRepository = userRepository;
        this.shortTimeoutRestTemplate = shortTimeoutRestTemplate;
        this.gitlab = gitlab;
        this.gitLabUserManagementService = gitLabUserManagementService;
        this.scheduler = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());
    }

    @Override
    public void configureRepository(ProgrammingExercise exercise, VcsRepositoryUrl repositoryUrl, Set<User> users, boolean allowAccess) {
        for (User user : users) {
            String username = user.getLogin();

            // TODO: does it really make sense to potentially create a user here? Should we not rather create this user when the user is created in the internal Artemis database?

            // Automatically created users
            if ((userPrefixEdx.isPresent() && username.startsWith(userPrefixEdx.get())) || (userPrefixU4I.isPresent() && username.startsWith((userPrefixU4I.get())))) {
                if (!userExists(username)) {
                    gitLabUserManagementService.importUser(user);
                }
            }
            if (allowAccess && !Boolean.FALSE.equals(exercise.isAllowOfflineIde())) {
                // only add access to the repository if the offline IDE usage is NOT explicitly disallowed
                // NOTE: null values are interpreted as offline IDE is allowed
                addMemberToRepository(repositoryUrl, user);
            }
        }

        protectBranch(repositoryUrl, "master");
    }

    @Override
    public void addMemberToRepository(VcsRepositoryUrl repositoryUrl, User user) {
        final var repositoryId = getPathIDFromRepositoryURL(repositoryUrl);
        final var userId = gitLabUserManagementService.getUserId(user.getLogin());

        try {
            gitlab.getProjectApi().addMember(repositoryId, userId, DEVELOPER);
        }
        catch (GitLabApiException e) {
            // A resource conflict status code is returned if the member
            // already exists in the repository
            if (e.getHttpStatus() == 409) {
                updateMemberPermissionInRepository(repositoryUrl, user.getLogin(), DEVELOPER);
            }
            else if (e.getValidationErrors().containsKey("access_level")
                    && e.getValidationErrors().get("access_level").stream().anyMatch(s -> s.contains("should be greater than or equal to"))) {
                log.warn("Member already has the requested permissions! Permission stays the same");
            }
            else {
                throw new GitLabException("Error while trying to add user to repository: " + user.getLogin() + " to repo " + repositoryUrl, e);
            }
        }
    }

    @Override
    public void removeMemberFromRepository(VcsRepositoryUrl repositoryUrl, User user) {
        final var repositoryId = getPathIDFromRepositoryURL(repositoryUrl);
        final var userId = gitLabUserManagementService.getUserId(user.getLogin());

        try {
            gitlab.getProjectApi().removeMember(repositoryId, userId);
        }
        catch (GitLabApiException e) {
            throw new GitLabException("Error while trying to remove user from repository: " + user.getLogin() + " from repo " + repositoryUrl, e);
        }
    }

    /**
     * Protects a branch from the repository, so that developers cannot change the history
     *
     * @param repositoryUrl     The repository url of the repository to update. It contains the project key & the repository name.
     * @param branch            The name of the branch to protect (e.g "master")
     * @throws VersionControlException      If the communication with the VCS fails.
     */
    private void protectBranch(VcsRepositoryUrl repositoryUrl, String branch) {
        final var repositoryId = getPathIDFromRepositoryURL(repositoryUrl);
        // we have to first unprotect the branch in order to set the correct access level, this is the case, because the master branch is protected for maintainers by default
        // Unprotect the branch in 8 seconds first and then protect the branch in 12 seconds.
        // We do this to wait on any async calls to Gitlab and make sure that the branch really exists before protecting it.
        unprotectBranch(repositoryId, branch, 8L, TimeUnit.SECONDS);
        protectBranch(repositoryId, branch, 12L, TimeUnit.SECONDS);
    }

    /**
     * Protects the branch but delays the execution.
     *
     * @param repositoryId  The id of the repository
     * @param branch        The branch to protect
     * @param delayTime     Time until the call is executed
     * @param delayTimeUnit The unit of the time (e.g seconds, minutes)
     */
    private void protectBranch(String repositoryId, String branch, Long delayTime, TimeUnit delayTimeUnit) {
        scheduler.schedule(() -> {
            try {
                log.info("Protecting branch " + branch + "for Gitlab repository " + repositoryId);
                gitlab.getProtectedBranchesApi().protectBranch(repositoryId, branch, DEVELOPER, DEVELOPER, MAINTAINER, false);
            }
            catch (GitLabApiException e) {
                throw new GitLabException("Unable to protect branch " + branch + " for repository " + repositoryId, e);
            }
        }, delayTime, delayTimeUnit);
    }

    @Override
    public void unprotectBranch(VcsRepositoryUrl repositoryUrl, String branch) throws VersionControlException {
        final var repositoryId = getPathIDFromRepositoryURL(repositoryUrl);
        // Unprotect the branch in 10 seconds. We do this to wait on any async calls to Gitlab and make sure that the branch really exists before unprotecting it.
        unprotectBranch(repositoryId, branch, 10L, TimeUnit.SECONDS);
    }

    /**
     * Unprotects the branch but delays the execution.
     *
     * @param repositoryId  The id of the repository
     * @param branch        The branch to unprotect
     * @param delayTime     Time until the call is executed
     * @param delayTimeUnit The unit of the time (e.g seconds, minutes)
     */
    private void unprotectBranch(String repositoryId, String branch, Long delayTime, TimeUnit delayTimeUnit) {
        scheduler.schedule(() -> {
            try {
                log.info("Unprotecting branch " + branch + "for Gitlab repository " + repositoryId);
                gitlab.getProtectedBranchesApi().unprotectBranch(repositoryId, branch);
            }
            catch (GitLabApiException e) {
                throw new GitLabException("Could not unprotect branch " + branch + " for repository " + repositoryId, e);
            }
        }, delayTime, delayTimeUnit);
    }

    @Override
    public void addWebHooksForExercise(ProgrammingExercise exercise) {
        super.addWebHooksForExercise(exercise);
        final var projectKey = exercise.getProjectKey();

        // Optional webhook from the version control system to the continuous integration system
        // This allows the continuous integration system to immediately build when new commits are pushed (in contrast to pulling regurlarly)
        final var templatePlanNotificationUrl = getContinuousIntegrationService().getWebHookUrl(projectKey, exercise.getTemplateParticipation().getBuildPlanId());
        final var solutionPlanNotificationUrl = getContinuousIntegrationService().getWebHookUrl(projectKey, exercise.getSolutionParticipation().getBuildPlanId());
        if (templatePlanNotificationUrl.isPresent() && solutionPlanNotificationUrl.isPresent()) {
            addAuthenticatedWebHook(exercise.getVcsTemplateRepositoryUrl(), templatePlanNotificationUrl.get(), "Artemis Exercise WebHook", ciToken);
            addAuthenticatedWebHook(exercise.getVcsSolutionRepositoryUrl(), solutionPlanNotificationUrl.get(), "Artemis Solution WebHook", ciToken);
            addAuthenticatedWebHook(exercise.getVcsTestRepositoryUrl(), solutionPlanNotificationUrl.get(), "Artemis Tests WebHook", ciToken);
        }
    }

    @Override
    public void addWebHookForParticipation(ProgrammingExerciseParticipation participation) {
        if (!participation.getInitializationState().hasCompletedState(InitializationState.INITIALIZED)) {
            super.addWebHookForParticipation(participation);

            // Optional webhook from the version control system to the continuous integration system
            // This allows the continuous integration system to immediately build when new commits are pushed (in contrast to pulling regurlarly)
            getContinuousIntegrationService().getWebHookUrl(participation.getProgrammingExercise().getProjectKey(), participation.getBuildPlanId())
                    .ifPresent(hookUrl -> addAuthenticatedWebHook(participation.getVcsRepositoryUrl(), hookUrl, "Artemis trigger to CI", ciToken));
        }
    }

    @Override
    protected void addWebHook(VcsRepositoryUrl repositoryUrl, String notificationUrl, String webHookName) {
        addAuthenticatedWebHook(repositoryUrl, notificationUrl, webHookName, "noSecretNeeded");
    }

    @Override
    protected void addAuthenticatedWebHook(VcsRepositoryUrl repositoryUrl, String notificationUrl, String webHookName, String secretToken) {
        final var repositoryId = getPathIDFromRepositoryURL(repositoryUrl);
        final var hook = new ProjectHook().withPushEvents(true).withIssuesEvents(false).withMergeRequestsEvents(false).withWikiPageEvents(false);

        try {
            gitlab.getProjectApi().addHook(repositoryId, notificationUrl, hook, false, secretToken);
        }
        catch (GitLabApiException e) {
            throw new GitLabException("Unable to add webhook for " + repositoryUrl, e);
        }
    }

    @Override
    public void deleteProject(String projectKey) {
        try {
            gitlab.getGroupApi().deleteGroup(projectKey);
        }
        catch (GitLabApiException e) {
            // Do not throw an exception if we try to delete a non-existant repository.
            if (e.getHttpStatus() != 404) {
                throw new GitLabException("Unable to delete group in GitLab: " + projectKey, e);
            }
        }
    }

    @Override
    public void deleteRepository(VcsRepositoryUrl repositoryUrl) {
        final var repositoryId = getPathIDFromRepositoryURL(repositoryUrl);
        final var repositoryName = urlService.getRepositorySlugFromRepositoryUrl(repositoryUrl);
        try {
            gitlab.getProjectApi().deleteProject(repositoryId);
        }
        catch (GitLabApiException e) {
            // Do not throw an exception if we try to delete a non-existant repository.
            if (e.getHttpStatus() != HttpStatus.SC_NOT_FOUND) {
                throw new GitLabException("Error trying to delete repository on GitLab: " + repositoryName, e);
            }
        }
    }

    @Override
    public VcsRepositoryUrl getCloneRepositoryUrl(String projectKey, String repositorySlug) {
        return new GitLabRepositoryUrl(projectKey, repositorySlug);
    }

    @Override
    public Boolean repositoryUrlIsValid(@Nullable VcsRepositoryUrl repositoryUrl) {
        if (repositoryUrl == null || repositoryUrl.getURL() == null) {
            return false;
        }
        final var repositoryId = getPathIDFromRepositoryURL(repositoryUrl);
        try {
            gitlab.getProjectApi().getProject(repositoryId);
        }
        catch (Exception emAll) {
            log.warn("Invalid repository VcsRepositoryUrl " + repositoryUrl);
            return false;
        }

        return true;
    }

    @Override
    public Commit getLastCommitDetails(Object requestBody) throws VersionControlException {
        final var details = GitLabPushNotificationDTO.convert(requestBody);
        final var commit = new Commit();
        // We will notify for every commit, so we can just use the first commit in the notification list
        final var gitLabCommit = details.getCommits().get(0);
        commit.setMessage(gitLabCommit.getMessage());
        commit.setAuthorEmail(gitLabCommit.getAuthor().getEmail());
        commit.setAuthorName(gitLabCommit.getAuthor().getName());
        final var ref = details.getRef().split("/");
        commit.setBranch(ref[ref.length - 1]);
        commit.setCommitHash(gitLabCommit.getHash());

        return commit;
    }

    @Override
    public void createProjectForExercise(ProgrammingExercise programmingExercise) throws VersionControlException {
        final var exercisePath = programmingExercise.getProjectKey();
        final var exerciseName = exercisePath + " " + programmingExercise.getTitle();

        final var group = new Group().withPath(exercisePath).withName(exerciseName).withVisibility(Visibility.PRIVATE);
        try {
            gitlab.getGroupApi().addGroup(group);
        }
        catch (GitLabApiException e) {
            if (e.getMessage().contains("has already been taken")) {
                // ignore this error, because it is not really a problem
                log.warn("Failed to add group " + exerciseName + " due to error: " + e.getMessage());
            }
            else {
                throw new GitLabException("Unable to create new group for course " + exerciseName, e);
            }
        }
        final var instructors = userRepository.getInstructors(programmingExercise.getCourseViaExerciseGroupOrCourseMember());
        final var tutors = userRepository.getTutors(programmingExercise.getCourseViaExerciseGroupOrCourseMember());
        for (final var instructor : instructors) {
            try {
                final var userId = gitLabUserManagementService.getUserId(instructor.getLogin());
                gitLabUserManagementService.addUserToGroups(userId, List.of(programmingExercise), MAINTAINER);
            }
            catch (GitLabException ignored) {
                // ignore the exception and continue with the next user, one non existing user or issue here should not prevent the creation of the whole programming exercise
            }
        }
        for (final var tutor : tutors) {
            try {
                final var userId = gitLabUserManagementService.getUserId(tutor.getLogin());
                gitLabUserManagementService.addUserToGroups(userId, List.of(programmingExercise), REPORTER);
            }
            catch (GitLabException ignored) {
                // ignore the exception and continue with the next user, one non existing user or issue here should not prevent the creation of the whole programming exercise
            }
        }
    }

    @Override
    public void createRepository(String projectKey, String repoName, String parentProjectKey) throws VersionControlException {
        try {
            final var groupId = gitlab.getGroupApi().getGroup(projectKey).getId();
            final var project = new Project().withName(repoName.toLowerCase()).withNamespaceId(groupId).withVisibility(Visibility.PRIVATE).withJobsEnabled(false)
                    .withSharedRunnersEnabled(false).withContainerRegistryEnabled(false);
            gitlab.getProjectApi().createProject(project);
        }
        catch (GitLabApiException e) {
            if (e.getValidationErrors().containsKey("path") && e.getValidationErrors().get("path").contains("has already been taken")) {
                log.info("Repository {} (parent {}) already exists, reusing it...", repoName, projectKey);
                return;
            }
            throw new GitLabException("Error creating new repository " + repoName, e);
        }
    }

    @Override
    public boolean checkIfProjectExists(String projectKey, String projectName) {
        try {
            return !gitlab.getProjectApi().getProjects(projectKey).isEmpty();
        }
        catch (GitLabApiException e) {
            throw new GitLabException("Error trying to search for project " + projectName, e);
        }
    }

    @Override
    public void setRepositoryPermissionsToReadOnly(VcsRepositoryUrl repositoryUrl, String projectKey, Set<User> users) {
        users.forEach(user -> updateMemberPermissionInRepository(repositoryUrl, user.getLogin(), REPORTER));
    }

    /**
     * Updates the acess level of the user if it's a member of the repository.
     * @param repositoryUrl The url of the repository
     * @param username the username of the gitlab user
     * @param accessLevel the new access level for the user
     */
    private void updateMemberPermissionInRepository(VcsRepositoryUrl repositoryUrl, String username, AccessLevel accessLevel) {
        final var userId = gitLabUserManagementService.getUserId(username);
        final var repositoryId = getPathIDFromRepositoryURL(repositoryUrl);
        try {
            gitlab.getProjectApi().updateMember(repositoryId, userId, accessLevel);
        }
        catch (GitLabApiException e) {
            throw new GitLabException("Unable to set permissions for user " + username + ". Trying to set permission " + accessLevel, e);
        }
    }

    @Override
    public ConnectorHealth health() {
        try {
            final var uri = Endpoints.HEALTH.buildEndpoint(gitlabServerUrl.toString()).build().toUri();
            final var healthResponse = shortTimeoutRestTemplate.getForObject(uri, JsonNode.class);
            final var status = healthResponse.get("status").asText();
            if (!status.equals("ok")) {
                return new ConnectorHealth(false, Map.of("status", status, "url", gitlabServerUrl));
            }
            return new ConnectorHealth(true, Map.of("url", gitlabServerUrl));
        }
        catch (Exception emAll) {
            return new ConnectorHealth(emAll);
        }
    }

    private boolean userExists(String username) {
        try {
            return gitlab.getUserApi().getUser(username) != null;
        }
        catch (GitLabApiException e) {
            throw new GitLabException("Unable to fetch user ID for " + username, e);
        }
    }

    private String getPathIDFromRepositoryURL(VcsRepositoryUrl repositoryUrl) {
        final var namespaces = repositoryUrl.getURL().toString().split("/");
        final var last = namespaces.length - 1;

        return namespaces[last - 1] + "/" + namespaces[last].replace(".git", "");
    }

    private enum Endpoints {

        ADD_USER("projects", "<projectId>", "members"), USERS("users"), EDIT_EXERCISE_PERMISSION("projects", "<projectId>", "members", "<memberId>"),
        PROTECTED_BRANCHES("projects", "<projectId>", "protected_branches"), PROTECTED_BRANCH("projects", "<projectId>", "protected_branches", "<branchName>"),
        GET_WEBHOOKS("projects", "<projectId>", "hooks"), ADD_WEBHOOK("projects", "<projectId>", "hooks"), COMMITS("projects", "<projectId>", "repository", "commits"),
        GROUPS("groups"), NAMESPACES("namespaces", "<groupId>"), DELETE_GROUP("groups", "<groupId>"), DELETE_PROJECT("projects", "<projectId>"), PROJECTS("projects"),
        GET_PROJECT("projects", "<projectId>"), FORK("projects", "<projectId>", "fork"), HEALTH("-", "liveness");

        private final List<String> pathSegments;

        Endpoints(String... pathSegments) {
            this.pathSegments = Arrays.asList(pathSegments);
        }

        public UriComponentsBuilder buildEndpoint(String baseUrl, Object... args) {
            return UrlUtils.buildEndpoint(baseUrl, pathSegments, args);
        }
    }

    public final class GitLabRepositoryUrl extends VcsRepositoryUrl {

        public GitLabRepositoryUrl(String projectKey, String repositorySlug) {
            final var path = projectKey + "/" + repositorySlug;
            final var urlString = gitlabServerUrl + "/" + path + ".git";

            stringToURL(urlString);
        }

        private GitLabRepositoryUrl(String urlString) {
            stringToURL(urlString);
        }

        private void stringToURL(String urlString) {
            try {
                this.url = new URL(urlString);
            }
            catch (MalformedURLException e) {
                throw new GitLabException("Could not build GitLab URL", e);
            }
        }

        @Override
        public VcsRepositoryUrl withUser(String username) {
            this.username = username;
            return new GitLabRepositoryUrl(url.toString().replaceAll("(https?://)(.*)", "$1" + username + "@$2"));
        }
    }
}
