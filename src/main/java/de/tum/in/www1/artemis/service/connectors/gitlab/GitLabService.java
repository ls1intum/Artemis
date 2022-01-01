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

    public GitLabService(UserRepository userRepository, @Qualifier("shortTimeoutGitlabRestTemplate") RestTemplate shortTimeoutRestTemplate, GitLabApi gitlab, UrlService urlService,
            GitLabUserManagementService gitLabUserManagementService, GitService gitService, ApplicationContext applicationContext) {
        super(applicationContext, gitService, urlService);
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
                    gitLabUserManagementService.createUser(user);
                }
            }
            if (allowAccess && !Boolean.FALSE.equals(exercise.isAllowOfflineIde())) {
                // only add access to the repository if the offline IDE usage is NOT explicitly disallowed
                // NOTE: null values are interpreted as offline IDE is allowed
                addMemberToRepository(repositoryUrl, user);
            }
        }

        var defaultBranch = getDefaultBranchOfRepository(repositoryUrl);
        protectBranch(repositoryUrl, defaultBranch);
    }

    @Override
    public void addMemberToRepository(VcsRepositoryUrl repositoryUrl, User user) {
        final var repositoryPath = urlService.getRepositoryPathFromRepositoryUrl(repositoryUrl);
        final var userId = gitLabUserManagementService.getUserId(user.getLogin());

        try {
            log.info("repositoryPath: " + repositoryPath + ", userId: " + userId);
            gitlab.getProjectApi().addMember(repositoryPath, userId, DEVELOPER);
        }
        catch (GitLabApiException e) {
            // A resource conflict status code is returned if the member
            // already exists in the repository
            if (e.getHttpStatus() == 409) {
                updateMemberPermissionInRepository(repositoryUrl, user.getLogin(), DEVELOPER);
            }
            else if (e.getValidationErrors() != null && e.getValidationErrors().containsKey("access_level")
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
        final var repositoryPath = urlService.getRepositoryPathFromRepositoryUrl(repositoryUrl);
        final var userId = gitLabUserManagementService.getUserId(user.getLogin());

        try {
            gitlab.getProjectApi().removeMember(repositoryPath, userId);
        }
        catch (GitLabApiException e) {
            throw new GitLabException("Error while trying to remove user from repository: " + user.getLogin() + " from repo " + repositoryUrl, e);
        }
    }

    /**
     * Get the default branch of the repository
     *
     * @param repositoryUrl The repository url to get the default branch for.
     * @return the name of the default branch, e.g. 'main'
     */
    @Override
    public String getDefaultBranchOfRepository(VcsRepositoryUrl repositoryUrl) throws GitLabException {
        var repositoryId = getPathIDFromRepositoryURL(repositoryUrl);

        try {
            return gitlab.getProjectApi().getProject(repositoryId).getDefaultBranch();
        }
        catch (GitLabApiException e) {
            throw new GitLabException("Unable to get default branch for repository " + repositoryId, e);
        }
    }

    private String getPathIDFromRepositoryURL(VcsRepositoryUrl repositoryUrl) {
        final var namespaces = repositoryUrl.getURL().toString().split("/");
        final var last = namespaces.length - 1;
        return namespaces[last - 1] + "/" + namespaces[last].replace(".git", "");
    }

    /**
     * Protects a branch from the repository, so that developers cannot change the history
     *
     * @param repositoryUrl     The repository url of the repository to update. It contains the project key & the repository name.
     * @param branch            The name of the branch to protect (e.g "master")
     * @throws VersionControlException      If the communication with the VCS fails.
     */
    private void protectBranch(VcsRepositoryUrl repositoryUrl, String branch) {
        final var repositoryPath = urlService.getRepositoryPathFromRepositoryUrl(repositoryUrl);
        // we have to first unprotect the branch in order to set the correct access level, this is the case, because the master branch is protected for maintainers by default
        // Unprotect the branch in 8 seconds first and then protect the branch in 12 seconds.
        // We do this to wait on any async calls to Gitlab and make sure that the branch really exists before protecting it.
        unprotectBranch(repositoryPath, branch, 8L, TimeUnit.SECONDS);
        protectBranch(repositoryPath, branch, 12L, TimeUnit.SECONDS);
    }

    /**
     * Protects the branch but delays the execution.
     *
     * @param repositoryPath  The id of the repository
     * @param branch        The branch to protect
     * @param delayTime     Time until the call is executed
     * @param delayTimeUnit The unit of the time (e.g seconds, minutes)
     */
    private void protectBranch(String repositoryPath, String branch, Long delayTime, TimeUnit delayTimeUnit) {
        scheduler.schedule(() -> {
            try {
                log.info("Protecting branch {} for Gitlab repository {}", branch, repositoryPath);
                gitlab.getProtectedBranchesApi().protectBranch(repositoryPath, branch, DEVELOPER, DEVELOPER, OWNER, false);
            }
            catch (GitLabApiException e) {
                throw new GitLabException("Unable to protect branch " + branch + " for repository " + repositoryPath, e);
            }
        }, delayTime, delayTimeUnit);
    }

    @Override
    public void unprotectBranch(VcsRepositoryUrl repositoryUrl, String branch) throws VersionControlException {
        final var repositoryPath = urlService.getRepositoryPathFromRepositoryUrl(repositoryUrl);
        // Unprotect the branch in 10 seconds. We do this to wait on any async calls to Gitlab and make sure that the branch really exists before unprotecting it.
        unprotectBranch(repositoryPath, branch, 10L, TimeUnit.SECONDS);
    }

    /**
     * Unprotect the branch but delays the execution.
     *
     * @param repositoryPath  The id of the repository
     * @param branch        The branch to unprotect
     * @param delayTime     Time until the call is executed
     * @param delayTimeUnit The unit of the time (e.g seconds, minutes)
     */
    private void unprotectBranch(String repositoryPath, String branch, Long delayTime, TimeUnit delayTimeUnit) {
        scheduler.schedule(() -> {
            try {
                log.info("Unprotecting branch {} for Gitlab repository {}", branch, repositoryPath);
                gitlab.getProtectedBranchesApi().unprotectBranch(repositoryPath, branch);
            }
            catch (GitLabApiException e) {
                throw new GitLabException("Could not unprotect branch " + branch + " for repository " + repositoryPath, e);
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
        final var repositoryPath = urlService.getRepositoryPathFromRepositoryUrl(repositoryUrl);
        final var hook = new ProjectHook().withPushEvents(true).withIssuesEvents(false).withMergeRequestsEvents(false).withWikiPageEvents(false);

        try {
            gitlab.getProjectApi().addHook(repositoryPath, notificationUrl, hook, false, secretToken);
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
        final var repositoryPath = urlService.getRepositoryPathFromRepositoryUrl(repositoryUrl);
        final var repositoryName = urlService.getRepositorySlugFromRepositoryUrl(repositoryUrl);
        try {
            gitlab.getProjectApi().deleteProject(repositoryPath);
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
        final var repositoryPath = urlService.getRepositoryPathFromRepositoryUrl(repositoryUrl);
        try {
            gitlab.getProjectApi().getProject(repositoryPath);
        }
        catch (Exception emAll) {
            log.warn("Invalid repository VcsRepositoryUrl {}", repositoryUrl);
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
        createGitlabGroupForExercise(programmingExercise);

        final Course course = programmingExercise.getCourseViaExerciseGroupOrCourseMember();

        final var instructors = userRepository.getInstructors(course);
        addUsersToExerciseGroup(instructors, programmingExercise, OWNER);

        // Get editors that are not instructors at the same time
        final var editors = userRepository.findAllInGroupContainingAndNotIn(course.getEditorGroupName(), new HashSet<>(instructors));
        addUsersToExerciseGroup(editors, programmingExercise, MAINTAINER);

        // Get teaching assistants that are not instructors nor editors
        final HashSet<User> instructorsAndEditors = new HashSet<>();
        instructorsAndEditors.addAll(instructors);
        instructorsAndEditors.addAll(editors);
        final var tutors = userRepository.findAllInGroupContainingAndNotIn(course.getTeachingAssistantGroupName(), instructorsAndEditors);
        addUsersToExerciseGroup(tutors, programmingExercise, REPORTER);
    }

    /**
     * Creates a new group in Gitlab for the specified programming exercise.
     *
     * @param programmingExercise the programming exercise
     */
    private void createGitlabGroupForExercise(ProgrammingExercise programmingExercise) {
        final String exercisePath = programmingExercise.getProjectKey();
        final String exerciseName = exercisePath + " " + programmingExercise.getTitle();
        final Group group = new Group().withPath(exercisePath).withName(exerciseName).withVisibility(Visibility.PRIVATE);

        try {
            gitlab.getGroupApi().addGroup(group);
        }
        catch (GitLabApiException e) {
            if (e.getMessage().contains("has already been taken")) {
                // ignore this error, because it is not really a problem
                log.warn("Failed to add group {} due to error: {}", exerciseName, e.getMessage());
            }
            else {
                throw new GitLabException("Unable to create new group for course " + exerciseName, e);
            }
        }
    }

    /**
     * Adds the users to the exercise's group with the specified access level.
     *
     * @param users The users to add
     * @param exercise the exercise
     * @param accessLevel the access level to give
     */
    private void addUsersToExerciseGroup(List<User> users, ProgrammingExercise exercise, AccessLevel accessLevel) {
        for (final var user : users) {
            try {
                final var userId = gitLabUserManagementService.getUserId(user.getLogin());
                gitLabUserManagementService.addUserToGroupsOfExercises(userId, List.of(exercise), accessLevel);
            }
            catch (GitLabException e) {
                // ignore the exception and continue with the next user, one non existing user or issue here should not
                // prevent the creation of the whole programming exercise
                log.warn("Skipped adding user {} to groups of exercise {}: {}", user.getLogin(), exercise, e.getMessage());
            }
        }
    }

    @Override
    public void createRepository(String projectKey, String repoName, String parentProjectKey) throws VersionControlException {
        try {
            final var groupId = gitlab.getGroupApi().getGroup(projectKey).getId();
            final var project = new Project().withPath(repoName.toLowerCase()).withName(repoName.toLowerCase()).withNamespaceId(groupId).withVisibility(Visibility.PRIVATE)
                    .withJobsEnabled(false).withSharedRunnersEnabled(false).withContainerRegistryEnabled(false);
            gitlab.getProjectApi().createProject(project);
        }
        catch (GitLabApiException e) {
            if (e.getValidationErrors() != null && e.getValidationErrors().containsKey("path") && e.getValidationErrors().get("path").contains("has already been taken")) {
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
        final var repositoryPath = urlService.getRepositoryPathFromRepositoryUrl(repositoryUrl);
        try {
            gitlab.getProjectApi().updateMember(repositoryPath, userId, accessLevel);
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
