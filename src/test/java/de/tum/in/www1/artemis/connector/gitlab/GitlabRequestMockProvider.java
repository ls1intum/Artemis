package de.tum.in.www1.artemis.connector.gitlab;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.gitlab4j.api.*;
import org.gitlab4j.api.models.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;

@Component
@Profile("gitlab")
public class GitlabRequestMockProvider {

    @Value("${artemis.version-control.url}")
    private URL GITLAB_SERVER_URL;

    private final RestTemplate restTemplate;

    private MockRestServiceServer mockServer;

    private final ObjectMapper mapper = new ObjectMapper();

    @SpyBean
    @InjectMocks
    private GitLabApi gitLabApi;

    @Mock
    private ProjectApi projectApi;

    @Mock
    private GroupApi groupApi;

    @Mock
    private UserApi userApi;

    final private List<org.gitlab4j.api.models.User> mockGitLabUsers = new ArrayList<>();
    final private List<Member> mockGitLabMembers = new ArrayList<>();

    public GitlabRequestMockProvider(@Qualifier("gitlabRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void enableMockingOfRequests() {
        mockServer = MockRestServiceServer.createServer(restTemplate);
        MockitoAnnotations.initMocks(this);
    }

    public void setupMockUsers(List<User> users) throws GitLabApiException {
        for (User user : users) {
            final org.gitlab4j.api.models.User gitLabUser = new org.gitlab4j.api.models.User();
            gitLabUser.setId(Math.toIntExact(user.getId()));
            gitLabUser.setUsername(user.getLogin());
            gitLabUser.setName(user.getName());
            gitLabUser.setEmail(user.getEmail());
            //Setup the mock returns
            mockGitLabUsers.add(gitLabUser);
            mockGetUserByUsername(gitLabUser.getUsername());
        }
    }

    public void setupMockMembers(List<User> users) {
        for (User user : users) {
            final Member member = new Member();
            member.setId(Math.toIntExact(user.getId()));
            member.setName(user.getName());
            member.setUsername(user.getLogin());
            if (member.getUsername().length() > 10 && member.getUsername().substring(0, 9).equals("instructor")) {
                member.setAccessLevel(AccessLevel.MAINTAINER);
            } else {
                member.setAccessLevel(AccessLevel.GUEST);
            }
            mockGitLabMembers.add(member);
        }
    }

    private void mockGetUserByUsername(String username) throws GitLabApiException {
        org.gitlab4j.api.models.User user = findUserByUsername(username);
        doReturn(user).when(userApi).getUser(username);
    }

    private void mockAddUserToGroup(ProgrammingExercise exercise, Member member) throws GitLabApiException {
        doReturn(member).when(groupApi).addMember(exercise.getProjectKey(), member.getId(), member.getAccessLevel());
    }

    public void reset() {
        mockServer.reset();
    }

    public void mockCreateProjectForExercise(ProgrammingExercise exercise) throws GitLabApiException {
        final var exercisePath = exercise.getProjectKey();
        final var exerciseName = exercisePath + " " + exercise.getTitle();
        final Group group = new Group().withPath(exercisePath).withName(exerciseName).withVisibility(Visibility.PRIVATE);
        doReturn(group).when(groupApi).addGroup(group);
        for (final var member : mockGitLabMembers) {
            mockAddUserToGroup(exercise, member);
        }

    }

    public void mockUpdateUser() throws GitLabApiException {
        doReturn(new org.gitlab4j.api.models.User()).when(userApi).updateUser(any(), any());
    }

    public void mockCreateRepository(ProgrammingExercise exercise, String repositoryName) throws GitLabApiException {
        Namespace exerciseNamespace = new Namespace().withName(exercise.getProjectKey());
        final var project = new Project().withName(repositoryName.toLowerCase()).withNamespace(exerciseNamespace).withVisibility(Visibility.PRIVATE).withJobsEnabled(false)
                .withSharedRunnersEnabled(false).withContainerRegistryEnabled(false);
        final var group = new Group();
        group.setId(1);
        doReturn(group).when(groupApi).getGroup(exercise.getProjectKey());
        doReturn(project).when(projectApi).createProject(project);
    }

    public void mockCheckIfProjectExists(final ProgrammingExercise exercise, final boolean exists) throws GitLabApiException {
        Project foundProject = new Project();
        foundProject.setName(exercise.getProjectName() + (exists ? "" : "abc"));
        List<Project> result = new ArrayList<>();
        if (exists) {
            result.add(foundProject);
        }
        doReturn(result).when(projectApi).getProjects(exercise.getProjectKey());
    }

    public void mockAddAuthenticatedWebHook() throws GitLabApiException {
        final var hook = new ProjectHook().withPushEvents(true).withIssuesEvents(false).withMergeRequestsEvents(false).withWikiPageEvents(false);
        doReturn(hook).when(projectApi).addHook(any(), anyString(), any(ProjectHook.class), anyBoolean(), anyString());
    }

    public void mockCopyRepositoryForParticipation(ProgrammingExercise exercise, String username) throws URISyntaxException, IOException {
        final var projectKey = exercise.getProjectKey();
        final var templateRepoName = exercise.getProjectKey().toLowerCase() + "-" + RepositoryType.TEMPLATE.getName();
        final var clonedRepoName = projectKey.toLowerCase() + "-" + username.toLowerCase();
        mockCopyRepository(projectKey, projectKey, templateRepoName, clonedRepoName);
    }

    private void mockCopyRepository(String sourceProjectKey, String targetProjectKey, String sourceRepoName, String targetRepoName)
            throws URISyntaxException, JsonProcessingException {
        final var originPath = sourceProjectKey + "%2F" + sourceRepoName.toLowerCase();
        final var targetRepoSlug = targetRepoName.toLowerCase();

        final var copyRepoPath = UriComponentsBuilder.fromUri(GITLAB_SERVER_URL.toURI()).path("/api/v4/projects").pathSegment(originPath).pathSegment("fork").build(true).toUri();
        final var body = Map.of("namespace", targetProjectKey, "path", targetRepoSlug, "name", targetRepoSlug);

        mockServer.expect(requestTo(copyRepoPath)).andExpect(method(HttpMethod.POST)).andExpect(content().json(mapper.writeValueAsString(body)))
                .andRespond(withStatus(HttpStatus.CREATED));
    }

    public void mockConfigureRepository(ProgrammingExercise exercise, String username) throws URISyntaxException {
        final var projectKey = exercise.getProjectKey();
        final var repoName = projectKey.toLowerCase() + "-" + username.toLowerCase();
        for (final var user : mockGitLabUsers) {
            mockGiveWritePermission(exercise, repoName, user.getUsername());
        }
        mockProtectBranches(exercise, repoName);
    }

    private org.gitlab4j.api.models.User findUserByUsername(final String username) {
        return mockGitLabUsers.stream()
            .filter(user -> username.equals(user.getUsername()))
            .findAny()
            .orElse(null);
    }

    private void mockGiveWritePermission(ProgrammingExercise exercise, String repositoryName, String username) throws URISyntaxException {
        final var projectKey = exercise.getProjectKey();
        final var projectPath = projectKey + "%2F" + repositoryName.toLowerCase();
        final var permissionPath = UriComponentsBuilder.fromUri(GITLAB_SERVER_URL.toURI()).path("/api/v4/projects").pathSegment(projectPath).pathSegment("members")
                .pathSegment(username).queryParam("access_level", "30").build().toUri();

        mockServer.expect(requestTo(permissionPath)).andExpect(method(HttpMethod.PUT)).andRespond(withStatus(HttpStatus.OK));
    }

    private void mockProtectBranches(ProgrammingExercise exercise, String repositoryName) throws URISyntaxException {
        final var projectPath = exercise.getProjectKey() + "%2F" + repositoryName.toLowerCase();
        final var protectBranchPath = UriComponentsBuilder.fromUri(GITLAB_SERVER_URL.toURI()).path("/api/v4/projects").pathSegment(projectPath).pathSegment("protected_branches")
                .queryParam("name", "*", "push_access_level", "30", "merge_access_level", "30").build().toUri();
        mockServer.expect(requestTo(protectBranchPath)).andExpect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.OK));
    }

}
