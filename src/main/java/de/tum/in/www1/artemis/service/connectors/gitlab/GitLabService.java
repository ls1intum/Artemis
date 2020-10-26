package de.tum.in.www1.artemis.service.connectors.gitlab;

import static org.gitlab4j.api.models.AccessLevel.*;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

import javax.annotation.PostConstruct;

import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;

import de.tum.in.www1.artemis.domain.Commit;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.VersionControlException;
import de.tum.in.www1.artemis.service.UrlService;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.service.connectors.AbstractVersionControlService;
import de.tum.in.www1.artemis.service.connectors.ConnectorHealth;
import de.tum.in.www1.artemis.service.connectors.gitlab.dto.GitLabPushNotificationDTO;
import de.tum.in.www1.artemis.service.util.UrlUtils;

@Profile("gitlab")
@Service
public class GitLabService extends AbstractVersionControlService {

    private final Logger log = LoggerFactory.getLogger(GitLabService.class);

    private static final String GITLAB_API_BASE = "/api/v4";

    @Value("${artemis.version-control.url}")
    private URL GITLAB_SERVER_URL;

    @Value("${artemis.lti.user-prefix-edx:#{null}}")
    private Optional<String> USER_PREFIX_EDX;

    @Value("${artemis.lti.user-prefix-u4i:#{null}}")
    private Optional<String> USER_PREFIX_U4I;

    @Value("${artemis.version-control.ci-token}")
    private String CI_TOKEN;

    private String BASE_API;

    private final UserService userService;

    private final RestTemplate restTemplate;

    private final GitLabUserManagementService gitLabUserManagementService;

    private final GitLabApi gitlab;

    private final UrlService urlService;

    public GitLabService(UserService userService, @Qualifier("gitlabRestTemplate") RestTemplate restTemplate, GitLabApi gitlab,
            GitLabUserManagementService gitLabUserManagementService, UrlService urlService) {
        this.userService = userService;
        this.restTemplate = restTemplate;
        this.gitlab = gitlab;
        this.gitLabUserManagementService = gitLabUserManagementService;
        this.urlService = urlService;
    }

    @PostConstruct
    public void init() {
        this.BASE_API = GITLAB_SERVER_URL + GITLAB_API_BASE;
    }

    @Override
    public void configureRepository(ProgrammingExercise exercise, URL repositoryUrl, Set<User> users, boolean allowAccess) {
        for (User user : users) {
            String username = user.getLogin();

            // Automatically created users
            if ((USER_PREFIX_EDX.isPresent() && username.startsWith(USER_PREFIX_EDX.get())) || (USER_PREFIX_U4I.isPresent() && username.startsWith((USER_PREFIX_U4I.get())))) {
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

        try {
            protectBranch("master", repositoryUrl);
        }
        catch (GitLabException ex) {
            log.warn("Could not protect branch (but will still continue) due to the following reason: " + ex.getMessage());
        }
    }

    @Override
    public void addMemberToRepository(URL repositoryUrl, User user) {
        final var repositoryId = getPathIDFromRepositoryURL(repositoryUrl);
        final var userId = gitLabUserManagementService.getUserId(user.getLogin());

        try {
            gitlab.getProjectApi().addMember(repositoryId, userId, DEVELOPER);
        }
        catch (GitLabApiException e) {
            if (e.getValidationErrors().containsKey("access_level")
                    && e.getValidationErrors().get("access_level").stream().anyMatch(s -> s.contains("should be greater than or equal to"))) {
                log.warn("Member already has the requested permissions! Permission stays the same");
            }
            else {
                throw new GitLabException("Error while trying to add user to repository: " + user.getLogin() + " to repo " + repositoryUrl, e);
            }
        }
    }

    @Override
    public void removeMemberFromRepository(URL repositoryUrl, User user) {
        final var repositoryId = getPathIDFromRepositoryURL(repositoryUrl);
        final var userId = gitLabUserManagementService.getUserId(user.getLogin());

        try {
            gitlab.getProjectApi().removeMember(repositoryId, userId);
        }
        catch (GitLabApiException e) {
            throw new GitLabException("Error while trying to remove user from repository: " + user.getLogin() + " from repo " + repositoryUrl, e);
        }
    }

    private void protectBranch(String branch, URL repositoryUrl) {
        final var repositoryId = getPathIDFromRepositoryURL(repositoryUrl);
        // we have to first unprotect the branch in order to set the correct access level
        try {
            unprotectBranch(repositoryId, branch);
        }
        catch (GitLabException ex) {
            log.warn("Could not unprotectBranch branch (but will try to protect it) due to the following reason: " + ex.getMessage());
        }

        try {
            gitlab.getProtectedBranchesApi().protectBranch(repositoryId, branch, DEVELOPER, DEVELOPER);
        }
        catch (GitLabApiException e) {
            throw new GitLabException("Unable to protect branch " + branch + " for repository " + repositoryUrl, e);
        }
    }

    private void unprotectBranch(String repositoryId, String branch) {
        try {
            gitlab.getRepositoryApi().unprotectBranch(repositoryId, branch);
        }
        catch (GitLabApiException e) {
            throw new GitLabException("Could not unprotect branch " + branch + " for repository " + repositoryId, e);
        }
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
            addAuthenticatedWebHook(exercise.getTemplateRepositoryUrlAsUrl(), templatePlanNotificationUrl.get(), "Artemis Exercise WebHook", CI_TOKEN);
            addAuthenticatedWebHook(exercise.getSolutionRepositoryUrlAsUrl(), solutionPlanNotificationUrl.get(), "Artemis Solution WebHook", CI_TOKEN);
            addAuthenticatedWebHook(exercise.getTestRepositoryUrlAsUrl(), solutionPlanNotificationUrl.get(), "Artemis Tests WebHook", CI_TOKEN);
        }
    }

    @Override
    public void addWebHookForParticipation(ProgrammingExerciseParticipation participation) {
        if (!participation.getInitializationState().hasCompletedState(InitializationState.INITIALIZED)) {
            super.addWebHookForParticipation(participation);

            // Optional webhook from the version control system to the continuous integration system
            // This allows the continuous integration system to immediately build when new commits are pushed (in contrast to pulling regurlarly)
            getContinuousIntegrationService().getWebHookUrl(participation.getProgrammingExercise().getProjectKey(), participation.getBuildPlanId())
                    .ifPresent(hookUrl -> addAuthenticatedWebHook(participation.getRepositoryUrlAsUrl(), hookUrl, "Artemis trigger to CI", CI_TOKEN));
        }
    }

    @Override
    protected void addWebHook(URL repositoryUrl, String notificationUrl, String webHookName) {
        addAuthenticatedWebHook(repositoryUrl, notificationUrl, webHookName, "noSecretNeeded");
    }

    @Override
    protected void addAuthenticatedWebHook(URL repositoryUrl, String notificationUrl, String webHookName, String secretToken) {
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
            throw new GitLabException("Unable to delete group in GitLab: " + projectKey, e);
        }
    }

    @Override
    public void deleteRepository(URL repositoryUrl) {
        final var repositoryId = getPathIDFromRepositoryURL(repositoryUrl);
        final var repositoryName = urlService.getRepositorySlugFromUrl(repositoryUrl);
        try {
            gitlab.getProjectApi().deleteProject(repositoryId);
        }
        catch (GitLabApiException e) {
            throw new GitLabException("Error trying to delete repository on GitLab: " + repositoryName, e);
        }
    }

    @Override
    public VcsRepositoryUrl getCloneRepositoryUrl(String projectKey, String repositorySlug) {
        return new GitLabRepositoryUrl(projectKey, repositorySlug);
    }

    @Override
    public Boolean repositoryUrlIsValid(URL repositoryUrl) {
        final var repositoryId = getPathIDFromRepositoryURL(repositoryUrl);
        try {
            gitlab.getProjectApi().getProject(repositoryId);
        }
        catch (Exception emAll) {
            log.warn("Invalid repository URL " + repositoryUrl);
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
        final var instructors = userService.getInstructors(programmingExercise.getCourseViaExerciseGroupOrCourseMember());
        final var teachingAssistants = userService.getTutors(programmingExercise.getCourseViaExerciseGroupOrCourseMember());
        for (final var instructor : instructors) {
            final var userId = gitLabUserManagementService.getUserId(instructor.getLogin());
            gitLabUserManagementService.addUserToGroups(userId, List.of(programmingExercise), MAINTAINER);
        }
        for (final var ta : teachingAssistants) {
            final var userId = gitLabUserManagementService.getUserId(ta.getLogin());
            gitLabUserManagementService.addUserToGroups(userId, List.of(programmingExercise), GUEST);
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
    public String getRepositoryName(URL repositoryUrl) {
        return urlService.getRepositorySlugFromUrl(repositoryUrl);
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
    public VcsRepositoryUrl copyRepository(String sourceProjectKey, String sourceRepositoryName, String targetProjectKey, String targetRepositoryName)
            throws VersionControlException {
        final var originalNamespace = sourceProjectKey + "%2F" + sourceRepositoryName.toLowerCase();
        final var targetRepoSlug = (targetProjectKey + "-" + targetRepositoryName).toLowerCase();
        final var builder = Endpoints.FORK.buildEndpoint(BASE_API, originalNamespace);
        final var body = Map.of("namespace", targetProjectKey, "path", targetRepoSlug, "name", targetRepoSlug);

        final var errorMessage = "Couldn't fork repository " + originalNamespace + " into " + targetRepoSlug;
        try {
            log.info("Try to fork " + originalNamespace + " into " + targetRepoSlug);
            final var response = restTemplate.postForEntity(builder.build(true).toUri(), body, String.class);
            if (response.getStatusCode() != HttpStatus.CREATED) {
                throw new GitLabException(errorMessage + "; response (" + response.getStatusCode() + ") was: " + response.getBody());
            }
        }
        catch (HttpClientErrorException e) {
            defaultExceptionHandling(errorMessage, e);
        }

        return new GitLabRepositoryUrl(targetProjectKey, targetRepoSlug);
    }

    @Override
    public void setRepositoryPermissionsToReadOnly(URL repositoryUrl, String projectKey, Set<User> users) {
        users.forEach(user -> setRepositoryPermission(repositoryUrl, user.getLogin(), GUEST));
    }

    private void setRepositoryPermission(URL repositoryUrl, String username, AccessLevel accessLevel) {
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
            final var uri = Endpoints.HEALTH.buildEndpoint(GITLAB_SERVER_URL.toString()).build().toUri();
            final var healthResponse = restTemplate.getForObject(uri, JsonNode.class);
            final var status = healthResponse.get("status").asText();
            if (!status.equals("ok")) {
                return new ConnectorHealth(false, Map.of("status", status, "url", GITLAB_SERVER_URL));
            }
            return new ConnectorHealth(true, Map.of("url", GITLAB_SERVER_URL));
        }
        catch (Exception emAll) {
            return new ConnectorHealth(emAll);
        }
    }

    private void defaultExceptionHandling(String message, HttpClientErrorException exception) {
        message = message + "; response was: " + exception.getResponseBodyAsString();
        log.error(message);
        throw new GitLabException(message, exception);
    }

    private boolean userExists(String username) {
        try {
            return gitlab.getUserApi().getUser(username) != null;
        }
        catch (GitLabApiException e) {
            throw new GitLabException("Unable to fetch user ID for " + username, e);
        }
    }

    private String getPathIDFromRepositoryURL(URL repository) {
        final var namespaces = repository.toString().split("/");
        final var last = namespaces.length - 1;

        return namespaces[last - 1] + "/" + namespaces[last].replace(".git", "");
    }

    private enum Endpoints {

        ADD_USER("projects", "<projectId>", "members"), USERS("users"), EDIT_EXERCISE_PERMISSION("projects", "<projectId>", "members", "<memberId>"),
        PROTECTED_BRANCHES("projects", "<projectId>", "protected_branches"), PROTECTED_BRANCH("projects", "<projectId>", "protected_branches", "<branchName>"),
        GET_WEBHOOKS("projects", "<projectId>", "hooks"), ADD_WEBHOOK("projects", "<projectId>", "hooks"), COMMITS("projects", "<projectId>", "repository", "commits"),
        GROUPS("groups"), NAMESPACES("namespaces", "<groupId>"), DELETE_GROUP("groups", "<groupId>"), DELETE_PROJECT("projects", "<projectId>"), PROJECTS("projects"),
        GET_PROJECT("projects", "<projectId>"), FORK("projects", "<projectId>", "fork"), HEALTH("-", "liveness");

        private List<String> pathSegments;

        Endpoints(String... pathSegments) {
            this.pathSegments = Arrays.asList(pathSegments);
        }

        public UriComponentsBuilder buildEndpoint(String baseUrl, Object... args) {
            return UrlUtils.buildEndpoint(baseUrl, pathSegments, args);
        }
    }

    public final class GitLabRepositoryUrl extends VcsRepositoryUrl {

        public GitLabRepositoryUrl(String projectKey, String repositorySlug) {
            super();
            final var path = projectKey + "/" + repositorySlug;
            final var urlString = GITLAB_SERVER_URL + "/" + path + ".git";

            stirngToURL(urlString);
        }

        private GitLabRepositoryUrl(String urlString) {
            stirngToURL(urlString);
        }

        private void stirngToURL(String urlString) {
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
