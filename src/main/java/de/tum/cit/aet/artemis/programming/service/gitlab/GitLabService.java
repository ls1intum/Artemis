package de.tum.cit.aet.artemis.programming.service.gitlab;

import static org.gitlab4j.api.models.AccessLevel.DEVELOPER;
import static org.gitlab4j.api.models.AccessLevel.MAINTAINER;
import static org.gitlab4j.api.models.AccessLevel.OWNER;
import static org.gitlab4j.api.models.AccessLevel.REPORTER;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import jakarta.annotation.Nullable;

import org.apache.http.HttpStatus;
import org.gitlab4j.api.Constants;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.AccessLevel;
import org.gitlab4j.api.models.Event;
import org.gitlab4j.api.models.Group;
import org.gitlab4j.api.models.Member;
import org.gitlab4j.api.models.Project;
import org.gitlab4j.api.models.ProjectHook;
import org.gitlab4j.api.models.Visibility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.VersionControlException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.service.connectors.ConnectorHealth;
import de.tum.cit.aet.artemis.core.util.UrlUtils;
import de.tum.cit.aet.artemis.programming.domain.Commit;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.gitlab.dto.GitLabPushNotificationDTO;
import de.tum.cit.aet.artemis.programming.service.vcs.AbstractVersionControlService;
import de.tum.cit.aet.artemis.programming.service.vcs.VersionControlRepositoryPermission;
import de.tum.cit.aet.artemis.service.UriService;

// Gitlab support will be removed in 8.0.0. Please migrate to LocalVC using e.g. the PR https://github.com/ls1intum/Artemis/pull/8972
@Deprecated(since = "7.5.0", forRemoval = true)

@Profile("gitlab")
@Service
public class GitLabService extends AbstractVersionControlService {

    private static final Logger log = LoggerFactory.getLogger(GitLabService.class);

    @Value("${artemis.version-control.url}")
    private URL gitlabServerUrl;

    @Value("${artemis.version-control.health-api-token:#{null}}")
    private Optional<String> healthToken;

    private final UserRepository userRepository;

    private final RestTemplate shortTimeoutRestTemplate;

    private final GitLabUserManagementService gitLabUserManagementService;

    private final GitLabApi gitlab;

    private final ScheduledExecutorService scheduler;

    public GitLabService(UserRepository userRepository, @Qualifier("shortTimeoutGitlabRestTemplate") RestTemplate shortTimeoutRestTemplate, GitLabApi gitlab, UriService uriService,
            GitLabUserManagementService gitLabUserManagementService, GitService gitService, ProgrammingExerciseStudentParticipationRepository studentParticipationRepository,
            ProgrammingExerciseRepository programmingExerciseRepository, TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository) {
        super(gitService, uriService, studentParticipationRepository, programmingExerciseRepository, templateProgrammingExerciseParticipationRepository,
                programmingExerciseBuildConfigRepository);
        this.userRepository = userRepository;
        this.shortTimeoutRestTemplate = shortTimeoutRestTemplate;
        this.gitlab = gitlab;
        this.gitLabUserManagementService = gitLabUserManagementService;
        this.scheduler = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());
    }

    @Override
    public void configureRepository(ProgrammingExercise exercise, ProgrammingExerciseStudentParticipation participation, boolean allowAccess) {
        for (User user : participation.getStudents()) {
            String username = user.getLogin();

            // This is a failsafe in case a user was not created in VCS on registration
            if (!userExists(username)) {
                throw new GitLabException("The user was not created in GitLab and has to be manually added.");
            }

            if (allowAccess) {
                final VersionControlRepositoryPermission permissions = determineRepositoryPermissions(exercise);
                addMemberToRepository(participation.getVcsRepositoryUri(), user, permissions);
            }

            // Validate that the access token exist, if it is required
            gitLabUserManagementService.generateVersionControlAccessTokenIfNecessary(user);
        }

        // TODO: we should separate access (above) from protecting branches
        String branch = getOrRetrieveBranchOfStudentParticipation(participation);
        protectBranch(participation.getVcsRepositoryUri(), branch);
    }

    @Override
    public void addMemberToRepository(VcsRepositoryUri repositoryUri, User user, VersionControlRepositoryPermission permissions) {
        final String repositoryPath = uriService.getRepositoryPathFromRepositoryUri(repositoryUri);
        final Long userId = gitLabUserManagementService.getUserId(user.getLogin());
        final AccessLevel repositoryPermissions = permissionsToAccessLevel(permissions);

        try {
            log.info("Adding user {} with permissions {} to repository {}", userId, repositoryPermissions, repositoryPath);
            gitlab.getProjectApi().addMember(repositoryPath, userId, repositoryPermissions);
        }
        catch (GitLabApiException e) {
            // A resource conflict status code is returned if the member
            // already exists in the repository
            if (e.getHttpStatus() == 409) {
                updateMemberPermissionInRepository(repositoryUri, user, permissions);
            }
            else if (e.getValidationErrors() != null && e.getValidationErrors().containsKey("access_level")
                    && e.getValidationErrors().get("access_level").stream().anyMatch(s -> s.contains("should be greater than or equal to"))) {
                log.warn("Member already has the requested permissions! Permission stays the same");
            }
            else {
                throw new GitLabException("Error while trying to add user to repository: " + user.getLogin() + " to repo " + repositoryUri, e);
            }
        }
    }

    private static AccessLevel permissionsToAccessLevel(final VersionControlRepositoryPermission permissions) {
        return switch (permissions) {
            case REPO_READ -> REPORTER;
            case REPO_WRITE -> DEVELOPER;
        };
    }

    @Override
    public void removeMemberFromRepository(VcsRepositoryUri repositoryUri, User user) {
        final var repositoryPath = uriService.getRepositoryPathFromRepositoryUri(repositoryUri);
        final var userId = gitLabUserManagementService.getUserId(user.getLogin());

        try {
            gitlab.getProjectApi().removeMember(repositoryPath, userId);
        }
        catch (GitLabApiException e) {
            throw new GitLabException("Error while trying to remove user from repository: " + user.getLogin() + " from repo " + repositoryUri, e);
        }
    }

    /**
     * Get the default branch of the repository
     *
     * @param repositoryUri The repository uri to get the default branch for.
     * @return the name of the default branch, e.g. 'main'
     */
    @Override
    public String getDefaultBranchOfRepository(VcsRepositoryUri repositoryUri) throws GitLabException {
        var repositoryId = getPathIDFromRepositoryURL(repositoryUri);

        try {
            return gitlab.getProjectApi().getProject(repositoryId).getDefaultBranch();
        }
        catch (GitLabApiException e) {
            throw new GitLabException("Unable to get default branch for repository " + repositoryId, e);
        }
    }

    private String getPathIDFromRepositoryURL(VcsRepositoryUri repositoryUri) {
        final var namespaces = repositoryUri.getURI().toString().split("/");
        final var last = namespaces.length - 1;
        return namespaces[last - 1] + "/" + namespaces[last].replace(".git", "");
    }

    /**
     * Protects a branch from the repository, so that developers cannot change the history
     *
     * @param repositoryUri The repository uri of the repository to update. It contains the project key & the repository name.
     * @param branch        The name of the branch to protect (e.g "main")
     * @throws VersionControlException If the communication with the VCS fails.
     */
    private void protectBranch(VcsRepositoryUri repositoryUri, String branch) {
        final var repositoryPath = uriService.getRepositoryPathFromRepositoryUri(repositoryUri);
        // we have to first unprotect the branch in order to set the correct access level, this is the case, because the main branch is protected for maintainers by default
        // Unprotect the branch in 8 seconds first and then protect the branch in 12 seconds.
        // We do this to wait on any async calls to Gitlab and make sure that the branch really exists before protecting it.
        unprotectBranch(repositoryPath, branch, 8L, TimeUnit.SECONDS);
        protectBranch(repositoryPath, branch, 12L, TimeUnit.SECONDS);
    }

    /**
     * Protects the branch but delays the execution.
     *
     * @param repositoryPath The id of the repository
     * @param branch         The branch to protect
     * @param delayTime      Time until the call is executed
     * @param delayTimeUnit  The unit of the time (e.g. seconds, minutes)
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
    public void unprotectBranch(VcsRepositoryUri repositoryUri, String branch) throws VersionControlException {
        final var repositoryPath = uriService.getRepositoryPathFromRepositoryUri(repositoryUri);
        // Unprotect the branch in 10 seconds. We do this to wait on any async calls to Gitlab and make sure that the branch really exists before unprotecting it.
        unprotectBranch(repositoryPath, branch, 10L, TimeUnit.SECONDS);
    }

    /**
     * Unprotect the branch but delays the execution.
     *
     * @param repositoryPath The id of the repository
     * @param branch         The branch to unprotect
     * @param delayTime      Time until the call is executed
     * @param delayTimeUnit  The unit of the time (e.g. seconds, minutes)
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
    protected void addWebHook(VcsRepositoryUri repositoryUri, String notificationUrl, String webHookName) {
        addAuthenticatedWebHook(repositoryUri, notificationUrl, webHookName, "noSecretNeeded");
    }

    @Override
    protected void addAuthenticatedWebHook(VcsRepositoryUri repositoryUri, String notificationUrl, String webHookName, String secretToken) {
        final var repositoryPath = uriService.getRepositoryPathFromRepositoryUri(repositoryUri);
        final var hook = new ProjectHook().withPushEvents(true).withIssuesEvents(false).withMergeRequestsEvents(false).withWikiPageEvents(false)
                // Note: Trigger hook on push events for matching branches only (this avoids unnecessary Jenkins builds for pushes to other branches)
                .withPushEventsBranchFilter(defaultBranch);

        try {
            gitlab.getProjectApi().addHook(repositoryPath, notificationUrl, hook, false, secretToken);
        }
        catch (GitLabApiException e) {
            throw new GitLabException("Unable to add webhook for " + repositoryUri, e);
        }
    }

    @Override
    public void deleteProject(String projectKey) {
        try {
            gitlab.getGroupApi().deleteGroup(projectKey);
        }
        catch (GitLabApiException e) {
            // Do not throw an exception if we try to delete a non-existent repository.
            if (e.getHttpStatus() != 404) {
                throw new GitLabException("Unable to delete group in GitLab: " + projectKey, e);
            }
        }
    }

    @Override
    public void deleteRepository(VcsRepositoryUri repositoryUri) {
        final var repositoryPath = uriService.getRepositoryPathFromRepositoryUri(repositoryUri);
        final var repositoryName = uriService.getRepositorySlugFromRepositoryUri(repositoryUri);
        try {
            gitlab.getProjectApi().deleteProject(repositoryPath);
        }
        catch (GitLabApiException e) {
            // Do not throw an exception if we try to delete a non-existent repository.
            if (e.getHttpStatus() != HttpStatus.SC_NOT_FOUND) {
                throw new GitLabException("Error trying to delete repository on GitLab: " + repositoryName, e);
            }
        }
    }

    @Override
    public VcsRepositoryUri getCloneRepositoryUri(String projectKey, String repositorySlug) {
        return new GitLabRepositoryUri(projectKey, repositorySlug);
    }

    @Override
    public Boolean repositoryUriIsValid(@Nullable VcsRepositoryUri repositoryUri) {
        if (repositoryUri == null || repositoryUri.getURI() == null) {
            return false;
        }
        final var repositoryPath = uriService.getRepositoryPathFromRepositoryUri(repositoryUri);
        try {
            gitlab.getProjectApi().getProject(repositoryPath);
        }
        catch (Exception emAll) {
            log.warn("Invalid repository VcsRepositoryUri {}", repositoryUri);
            return false;
        }

        return true;
    }

    @Override
    public Commit getLastCommitDetails(Object requestBody) throws VersionControlException {
        final var details = GitLabPushNotificationDTO.convert(requestBody);
        // Gitlab specifically provide the previous latest commit and the new latest commit after the given push event
        // Here we retrieve the hash of the new latest commit
        final var gitLabCommitHash = details.newHash();
        // Here we search for the commit details for the given commit hash
        // Technically these details should always be present but as this could change, we handle the edge case
        final var firstMatchingCommit = Optional.ofNullable(details.commits()).flatMap(commits -> commits.stream().filter(com -> gitLabCommitHash.equals(com.hash())).findFirst());
        if (firstMatchingCommit.isPresent()) {
            // Fill commit with commit details
            final var gitLabCommit = firstMatchingCommit.get();
            final var ref = details.ref().split("/");
            var branch = ref[ref.length - 1];
            var author = gitLabCommit.author();
            return new Commit(gitLabCommitHash, author.name(), gitLabCommit.message(), author.email(), branch);
        }
        return new Commit(gitLabCommitHash, null, null, null, null);
    }

    @Override
    public ZonedDateTime getPushDate(ProgrammingExerciseParticipation participation, String commitHash, Object eventObject) {
        // Gitlab never provides the push date initially so we can ignore the eventObject
        try {
            String repositoryUri = uriService.getRepositoryPathFromRepositoryUri(participation.getVcsRepositoryUri());
            Stream<Event> eventStream = gitlab.getEventsApi().getProjectEventsStream(repositoryUri, Constants.ActionType.PUSHED, null, null, null, Constants.SortOrder.DESC);
            var eventOptional = eventStream.filter(event -> commitHash.equals(event.getPushData().getCommitTo())).findFirst();

            if (eventOptional.isPresent()) {
                return eventOptional.get().getCreatedAt().toInstant().atZone(ZoneOffset.UTC);
            }
        }
        catch (GitLabApiException e) {
            throw new GitLabException(e);
        }
        throw new GitLabException("Could not find build queue date for participation " + participation.getId());
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
     * @param users       The users to add
     * @param exercise    the exercise
     * @param accessLevel the access level to give
     */
    private void addUsersToExerciseGroup(Set<User> users, ProgrammingExercise exercise, AccessLevel accessLevel) {
        for (final var user : users) {
            try {
                final var userId = gitLabUserManagementService.getUserId(user.getLogin());
                gitLabUserManagementService.addUserToGroupsOfExercises(userId, List.of(exercise), accessLevel);
            }
            catch (GitLabException e) {
                // ignore the exception and continue with the next user, one non-existing user or issue here should not
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
            // Groups in GitLab are named identically to the exercise `projectKey`.
            final Optional<Group> group = gitlab.getGroupApi().getOptionalGroup(projectKey);
            if (group.isEmpty()) {
                return false;
            }

            return gitlab.getGroupApi().getProjectsStream(group.get()).findAny().isPresent();
        }
        catch (GitLabApiException e) {
            throw new GitLabException("Error trying to search for project " + projectName, e);
        }
    }

    @Override
    public void setRepositoryPermissionsToReadOnly(VcsRepositoryUri repositoryUri, String projectKey, Set<User> users) {
        users.forEach(user -> updateMemberPermissionInRepository(repositoryUri, user, VersionControlRepositoryPermission.REPO_READ));
    }

    /**
     * Updates the access level of the user if it's a member of the repository.
     *
     * @param repositoryUri The url of the repository
     * @param user          The GitLab user
     * @param permissions   The new access level for the user
     */
    private void updateMemberPermissionInRepository(VcsRepositoryUri repositoryUri, User user, VersionControlRepositoryPermission permissions) {
        final var userId = gitLabUserManagementService.getUserId(user.getLogin());
        final var repositoryPath = uriService.getRepositoryPathFromRepositoryUri(repositoryUri);
        try {
            final Optional<Member> member = gitlab.getProjectApi().getOptionalMember(repositoryPath, userId);
            if (member.isPresent()) {
                final AccessLevel accessLevel = permissionsToAccessLevel(permissions);
                gitlab.getProjectApi().updateMember(repositoryPath, userId, accessLevel);
            }
            else {
                addMemberToRepository(repositoryUri, user, permissions);
            }
        }
        catch (GitLabApiException e) {
            throw new GitLabException("Unable to set permissions for user " + user.getLogin() + ". Trying to set permission " + permissions, e);
        }
    }

    @Override
    public ConnectorHealth health() {
        try {
            UriComponentsBuilder builder = Endpoints.HEALTH.buildEndpoint(gitlabServerUrl.toString());
            healthToken.ifPresent(token -> builder.queryParam("token", token));
            URI uri = builder.build().toUri();

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

    public final class GitLabRepositoryUri extends VcsRepositoryUri {

        public GitLabRepositoryUri(String projectKey, String repositorySlug) {
            final var path = projectKey + "/" + repositorySlug;
            final var urlString = gitlabServerUrl + "/" + path + ".git";

            stringToURL(urlString);
        }

        private GitLabRepositoryUri(String urlString) {
            stringToURL(urlString);
        }

        private void stringToURL(String urlString) {
            try {
                this.uri = new URI(urlString);
            }
            catch (URISyntaxException e) {
                throw new GitLabException("Could not build GitLab URL", e);
            }
        }

        @Override
        public VcsRepositoryUri withUser(String username) {
            this.username = username;
            return new GitLabRepositoryUri(uri.toString().replaceAll("(https?://)(.*)", "$1" + username + "@$2"));
        }
    }
}
