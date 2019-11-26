package de.tum.in.www1.artemis.service.connectors.gitlab;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.function.Supplier;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.exception.VersionControlException;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.service.connectors.ConnectorHealth;
import de.tum.in.www1.artemis.service.connectors.VersionControlService;

@Profile("gitlab")
@Service
public class GitLabService implements VersionControlService {

    private final Logger log = LoggerFactory.getLogger(GitLabService.class);

    @Value("${artemis.gitlab.url}")
    private URL GITLAB_SERVER_URL;

    @Value("${artemis.lti.user-prefix-edx}")
    private String USER_PREFIX_EDX = "";

    @Value("${artemis.lti.user-prefix-u4i}")
    private String USER_PREFIX_U4I = "";

    private String BASE_API;

    private final RestTemplate restTemplate;

    private final UserService userService;

    public GitLabService(@Qualifier("gitlabRestTemplate") RestTemplate restTemplate, UserService userService) {
        this.restTemplate = restTemplate;
        this.userService = userService;
    }

    @PostConstruct
    public void init() {
        this.BASE_API = GITLAB_SERVER_URL + "/api/v4";
    }

    @Override
    public void configureRepository(URL repositoryUrl, String username) {
        // Automatically created users
        if (username.startsWith(USER_PREFIX_EDX) || username.startsWith(USER_PREFIX_U4I)) {
            if (!userExists(username)) {
                final var user = userService.getUserByLogin(username).get();
                createUser(user);
            }
        }

        addMemberToProject(repositoryUrl, username);
        protectBranch("master", repositoryUrl);
    }

    private void createUser(User user) {
        final var body = Map.of("email", user.getEmail(), "password", user.getPassword(), "username", user.getLogin(), "name", user.getName(), "can_create_group", false);
        final var builder = Endpoints.USERS.buildEndpoint(BASE_API);

        final var errorMessage = "Unable to create new user in GitLab " + user.getLogin();
        performExchange(errorMessage, HttpStatus.CREATED, () -> restTemplate.postForEntity(builder.build(true).toUri(), body, String.class));
    }

    private void addMemberToProject(URL repositoryUrl, String username) {
        final var repositoryId = getPathIdFromRepositoryURL(repositoryUrl);
        final var userId = getUserId(username);
        final var builder = Endpoints.ADD_USER.buildEndpoint(BASE_API, repositoryId);
        final var body = Map.of("user_id", userId, "access_level", AccessLevel.DEVELOPER.levelCode);

        final var errorMessage = "Error while trying to add user to repository: " + username + " to repo " + repositoryUrl;
        performExchange(errorMessage, HttpStatus.CREATED, () -> restTemplate.postForEntity(builder.build(true).toUri(), body, String.class));
    }

    private void protectBranch(String branch, URL repositoryUrl) {
        final var repositoryId = getPathIdFromRepositoryURL(repositoryUrl);
        final var builder = Endpoints.PROTECTED_BRANCHES.buildEndpoint(BASE_API, repositoryId);
        final var body = Map.of("name", branch, "push_access_level", AccessLevel.DEVELOPER.levelCode);

        final var errorMesage = "Unable to protect branch " + branch + " for repository " + repositoryUrl;
        // we have to first unprotect the branch in order to set the correct access level
        unprotectBranch(repositoryId, branch);
        performExchange(errorMesage, HttpStatus.CREATED, () -> restTemplate.postForEntity(builder.build(true).toUri(), body, JsonNode.class));
    }

    private void unprotectBranch(String repositoryId, String branch) {
        final var builder = Endpoints.PROTECTED_BRANCH.buildEndpoint(BASE_API, repositoryId, branch);

        final var errorMessage = "Could not unprotect branch " + branch + " for repository " + repositoryId;
        performExchange(errorMessage, List.of(HttpStatus.NO_CONTENT, HttpStatus.NOT_FOUND),
                () -> restTemplate.exchange(builder.build(true).toUri(), HttpMethod.DELETE, null, String.class));
    }

    @Override
    public void addWebHook(URL repositoryUrl, String notificationUrl, String webHookName) {
        final var repositoryId = getPathIdFromRepositoryURL(repositoryUrl);
        if (!webhooksExists(repositoryId, notificationUrl)) {
            final var builder = Endpoints.ADD_WEBHOOK.buildEndpoint(BASE_API, repositoryId);
            final var body = Map.of("url", notificationUrl, "push_events", true, "enable_ssl_verification", false);

            final var errorMessage = "Unable to add webhook for " + repositoryUrl;
            performExchange(errorMessage, HttpStatus.CREATED, () -> restTemplate.postForEntity(builder.build(true).toUri(), body, JsonNode.class));
        }
    }

    private boolean webhooksExists(String repositoryId, String notificationUrl) {
        final var builder = Endpoints.GET_WEBHOOKS.buildEndpoint(BASE_API, repositoryId);

        final var errorMessage = "Unable to get webhooks for " + repositoryId;
        final var response = performExchange(errorMessage, HttpStatus.OK, () -> restTemplate.getForEntity(builder.build(true).toUri(), JsonNode.class));

        for (final var hook : response) {
            if (hook.get("url").asText().equals(notificationUrl))
                return true;
        }

        return false;
    }

    @Override
    public void deleteProject(String projectKey) {
        final var optionalGroup = groupExists(projectKey);
        if (optionalGroup.isPresent()) {
            final var builder = Endpoints.DELETE_GROUP.buildEndpoint(BASE_API, optionalGroup.get());

            final var errorMessage = "Unable to delete group in GitLab: " + projectKey;
            performExchange(errorMessage, HttpStatus.ACCEPTED, () -> restTemplate.exchange(builder.build(true).toUri(), HttpMethod.DELETE, null, String.class));
        }
    }

    @Override
    public void deleteRepository(URL repositoryUrl) {
        final var repositoryName = getRepositorySlugFromUrl(repositoryUrl);
        final var repositoryId = getRepositoryId(repositoryUrl);
        final var builder = Endpoints.DELETE_PROJECT.buildEndpoint(BASE_API, repositoryId);

        final var errorMessage = "Error trying to delete repository on GitLab: " + repositoryName;
        performExchange(errorMessage, HttpStatus.ACCEPTED, () -> restTemplate.exchange(builder.build(true).toUri(), HttpMethod.DELETE, null, String.class));
    }

    @Override
    public URL getRepositoryWebUrl(ProgrammingExerciseParticipation participation) {
        final var exercise = participation.getProgrammingExercise();
        final var courseKey = exercise.getCourse().getId();
        final var exerciseKey = exercise.getProjectKey();
        final var slug = getRepositorySlugFromUrl(participation.getRepositoryUrlAsUrl());

        try {
            return new URL(String.format("%s/%s/%s/%s", GITLAB_SERVER_URL, courseKey, exerciseKey, slug));
        }
        catch (MalformedURLException e) {
            log.error(e.getMessage());
            throw new GitLabException("Repository WEB URL cannot be built with", e);
        }
    }

    @Override
    public VcsRepositoryUrl getCloneRepositoryUrl(String projectKey, String repositorySlug) {
        return new GitLabRepositoryUrl(projectKey, repositorySlug);
    }

    @Override
    public Boolean repositoryUrlIsValid(URL repositoryUrl) {
        final var repositoryId = getPathIdFromRepositoryURL(repositoryUrl);
        final var builder = Endpoints.GET_PROJECT.buildEndpoint(BASE_API, repositoryId);

        try {
            final var errorMessage = "Can't get repository under the ID " + repositoryId;
            performExchange(errorMessage, HttpStatus.OK, () -> restTemplate.getForEntity(builder.build(true).toUri(), JsonNode.class));
        }
        catch (Exception emAll) {
            log.warn("Invalid repository URL " + repositoryUrl);
            return false;
        }

        return true;
    }

    @Override
    public Commit getLastCommitDetails(Object requestBody) throws VersionControlException {
        return null;
    }

    @Override
    public void createProjectForExercise(ProgrammingExercise programmingExercise) throws VersionControlException {
        final var exercisePath = programmingExercise.getProjectKey();
        final var exerciseName = exercisePath + " " + programmingExercise.getTitle();
        createGroup(exerciseName, exercisePath);
    }

    private Optional<Long> groupExists(String path) {
        final var builder = Endpoints.GROUPS.buildEndpoint(BASE_API).queryParam("search", path);

        final var errorString = "Unable to fetch groups matching " + path;
        final var foundGroups = performExchange(errorString, HttpStatus.OK, () -> restTemplate.getForEntity(builder.build(true).toUri(), JsonNode.class));

        for (final var group : foundGroups) {
            if (group.get("path").asText().equals(path))
                return Optional.of(group.get("id").asLong());
        }

        return Optional.empty();
    }

    private long createGroup(String groupName, String path) {
        final var builder = Endpoints.GROUPS.buildEndpoint(BASE_API);
        final var body = Map.<String, Object>of("name", groupName, "path", path, "visibility", "private");

        final var errorString = "Unable to create new group for course " + groupName;
        final var createdGroup = performExchange(errorString, HttpStatus.CREATED, () -> restTemplate.postForEntity(builder.build(true).toUri(), body, JsonNode.class));

        return createdGroup.get("id").asLong();
    }

    @Override
    public void createRepository(String projectKey, String repoName, String parentProjectKey) throws VersionControlException {
        final var exerciseGroupId = groupExists(projectKey).get();
        final var builder = Endpoints.PROJECTS.buildEndpoint(BASE_API);
        final var body = Map.of("name", repoName.toLowerCase(), "namespace_id", exerciseGroupId, "builds_access_level", "disabled", "visibility", "private");

        final var response = restTemplate.postForEntity(builder.build(true).toUri(), body, JsonNode.class);
        if (response.getStatusCode() == HttpStatus.BAD_REQUEST && response.getBody().get("message").get("name").get(0).asText().equals("has already been taken")) {
            log.info("Repository {} (parent {}) already exists, reusing it...", repoName, projectKey);
            return;
        }

        if (response.getStatusCode() != HttpStatus.CREATED) {
            throw new GitLabException("Error creating new repository " + repoName);
        }
    }

    @Override
    public String getRepositoryName(URL repositoryUrl) {
        return getRepositorySlugFromUrl(repositoryUrl);
    }

    private long getRepositoryId(URL repositoryUrl) {
        final var builder = Endpoints.PROJECTS.buildEndpoint(BASE_API).queryParam("search", getRepositorySlugFromUrl(repositoryUrl));

        final var errorMessage = "Error fetching ID for repository " + repositoryUrl;
        final var response = performExchange(errorMessage, HttpStatus.OK, () -> restTemplate.getForEntity(builder.build(true).toUri(), JsonNode.class));

        return response.get(0).get("id").asLong();
    }

    @Override
    public boolean checkIfProjectExists(String projectKey, String projectName) {
        final var builder = Endpoints.GROUPS.buildEndpoint(BASE_API).queryParam("search", projectKey);

        final var errorMessage = "Error trying to search for project " + projectKey;
        final var response = performExchange(errorMessage, HttpStatus.OK, () -> restTemplate.getForEntity(builder.build(true).toUri(), JsonNode.class));

        return !response.isEmpty();
    }

    @Override
    public VcsRepositoryUrl copyRepository(String sourceProjectKey, String sourceRepositoryName, String targetProjectKey, String targetRepositoryName)
            throws VersionControlException {
        final var originalNamespace = sourceProjectKey + "%2F" + sourceRepositoryName.toLowerCase();
        final var targetRepoSlug = (targetProjectKey + "-" + targetRepositoryName).toLowerCase();
        final var builder = Endpoints.FORK.buildEndpoint(BASE_API, originalNamespace);
        final var body = Map.of("namespace", targetProjectKey, "path", targetRepoSlug, "name", targetRepoSlug);

        final var errorMessage = "Couldn't fork repository: " + sourceProjectKey + " to " + targetProjectKey;
        performExchange(errorMessage, HttpStatus.CREATED, () -> restTemplate.postForEntity(builder.build(true).toUri(), body, String.class));

        return new GitLabRepositoryUrl(targetProjectKey, targetRepoSlug);
    }

    @Override
    public void setRepositoryPermissionsToReadOnly(URL repositoryUrl, String projectKey, String username) {
        setRepositoryPermission(repositoryUrl, username, AccessLevel.GUEST);
    }

    private void setRepositoryPermission(URL repositoryUrl, String username, AccessLevel accessLevel) {
        final var userId = getUserId(username);
        final var repositoryId = getPathIdFromRepositoryURL(repositoryUrl);
        final var builder = Endpoints.EDIT_EXERCISE_PERMISSION.buildEndpoint(BASE_API, repositoryId, userId);
        final var body = Map.of("access_level", accessLevel.levelCode);

        final var errorMessage = "Unable to set permissions for user " + username + ". Trying to set permission " + accessLevel;
        performExchange(errorMessage, HttpStatus.OK, () -> restTemplate.exchange(builder.build(true).toUri(), HttpMethod.PUT, new HttpEntity<>(body), JsonNode.class));

    }

    @Override
    public String getRepositorySlugFromUrl(URL repositoryUrl) throws VersionControlException {
        final var splittedUrl = repositoryUrl.toString().split("/");
        if (splittedUrl[splittedUrl.length - 1].matches(".*\\.git")) {
            return splittedUrl[splittedUrl.length - 1].replace(".git", "");
        }

        throw new GitLabException("Repository URL is not a git URL! Can't get slug for " + repositoryUrl.toString());
    }

    @Override
    public ConnectorHealth health() {
        return null;
    }

    private <T> T performExchange(String errorMessage, HttpStatus expectedStatus, Supplier<ResponseEntity<T>> tryToDo) {
        return performExchange(errorMessage, List.of(expectedStatus), tryToDo);
    }

    private <T> T performExchange(String errorMessage, List<HttpStatus> allowedStatuses, Supplier<ResponseEntity<T>> tryToDo) {
        try {
            final var response = tryToDo.get();
            if (!allowedStatuses.contains(response.getStatusCode())) {
                defaultExceptionHandling(errorMessage);
            }

            return response.getBody();
        }
        catch (HttpClientErrorException e) {
            defaultExceptionHandling(errorMessage, e);
        }

        // In theory unreachable, because we always throw an exception in the default handling
        throw new GitLabException("Unable to perform request.\n" + errorMessage);
    }

    private void defaultExceptionHandling(String message, Throwable exception) {
        log.error(message);
        throw new GitLabException(message, exception);
    }

    private void defaultExceptionHandling(String message) {
        log.error(message);
        throw new GitLabException(message);
    }

    private long getUserId(String username) {
        final var optionalUser = searchUser(username);

        if (optionalUser.isEmpty()) {
            final var errorString = "Unable to get ID for user " + username;
            log.error(errorString);
            throw new GitLabException(errorString);
        }

        return optionalUser.get();
    }

    private boolean userExists(String username) {
        return searchUser(username).isPresent();
    }

    private Optional<Long> searchUser(String username) {
        final var builder = Endpoints.USERS.buildEndpoint(BASE_API).queryParam("username", username);

        final var response = restTemplate.getForEntity(builder.toUriString(), JsonNode.class, new HashMap<>());

        if (response.getStatusCode() != HttpStatus.OK) {
            final var erroString = "Unable to fetch user ID for " + username;
            log.error(erroString);
            throw new GitLabException(erroString);
        }

        if (response.getBody() == null || response.getBody().size() == 0) {
            return Optional.empty();
        }

        return Optional.of(response.getBody().get(0).get("id").asLong());
    }

    private String getPathIdFromRepositoryURL(URL repository) {
        final var namespaces = repository.toString().split("/");
        final var last = namespaces.length - 1;

        return namespaces[last - 1] + "%2F" + namespaces[last].replace(".git", "");
    }

    private enum Endpoints {

        ADD_USER("projects", "<projectId>", "members"), USERS("users"), EDIT_EXERCISE_PERMISSION("projects", "<projectId>", "members", "<memberId>"),
        PROTECTED_BRANCHES("projects", "<projectId>", "protected_branches"), PROTECTED_BRANCH("projects", "<projectId>", "protected_branches", "<branchName>"),
        GET_WEBHOOKS("projects", "<projectId>", "hooks"), ADD_WEBHOOK("projects", "<projectId>", "hooks"), COMMITS("projects", "<projectId>", "repository", "commits"),
        GROUPS("groups"), NAMESPACES("namespaces", "<groupId>"), DELETE_GROUP("groups", "<groupId>"), DELETE_PROJECT("projects", "<projectId>"), PROJECTS("projects"),
        GET_PROJECT("projects", "<projectId>"), FORK("projects", "<projectId>", "fork");

        private List<String> pathSegments;

        Endpoints(String... pathSegments) {
            this.pathSegments = Arrays.asList(pathSegments);
        }

        public UriComponentsBuilder buildEndpoint(String baseUrl, Object... args) {
            for (int i = 0, segmentCtr = 0; i < pathSegments.size(); i++) {
                if (pathSegments.get(i).matches("<.*>")) {
                    if (segmentCtr == args.length) {
                        throw new IllegalArgumentException("Unable to build endpoint. Too few arguments!");
                    }
                    pathSegments.set(i, String.valueOf(args[segmentCtr++]));
                }
            }

            return UriComponentsBuilder.fromHttpUrl(baseUrl).pathSegment(pathSegments.toArray(new String[0]));
        }
    }

    private enum AccessLevel {

        GUEST(10), REPORTER(20), DEVELOPER(30), MAINTAINER(40), OWNER(50);

        private int levelCode;

        AccessLevel(int levelCode) {
            this.levelCode = levelCode;
        }

        public int getLevelCode() {
            return levelCode;
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
