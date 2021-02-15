package de.tum.in.www1.artemis.connector.gitlab;

import static org.gitlab4j.api.models.AccessLevel.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;

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
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;

@Component
@Profile("gitlab")
public class GitlabRequestMockProvider {

    @Value("${artemis.version-control.url}")
    private URL gitlabServerUrl;

    @Value("${artemis.lti.user-prefix-edx:#{null}}")
    private Optional<String> userPrefixEdx;

    @Value("${artemis.lti.user-prefix-u4i:#{null}}")
    private Optional<String> userPrefixU4I;

    private final RestTemplate restTemplate;

    private MockRestServiceServer mockServer;

    private final RestTemplate shortTimeoutRestTemplate;

    private MockRestServiceServer mockServerShortTimeout;

    @SpyBean
    @InjectMocks
    private GitLabApi gitLabApi;

    @Mock
    private ProjectApi projectApi;

    @Mock
    private GroupApi groupApi;

    @Mock
    private UserApi userApi;

    @Mock
    private RepositoryApi repositoryApi;

    @Mock
    private ProtectedBranchesApi protectedBranchesApi;

    public GitlabRequestMockProvider(@Qualifier("gitlabRestTemplate") RestTemplate restTemplate,
            @Qualifier("shortTimeoutGitlabRestTemplate") RestTemplate shortTimeoutRestTemplate) {
        this.restTemplate = restTemplate;
        this.shortTimeoutRestTemplate = shortTimeoutRestTemplate;
    }

    public void enableMockingOfRequests() {
        mockServer = MockRestServiceServer.createServer(restTemplate);
        mockServerShortTimeout = MockRestServiceServer.createServer(shortTimeoutRestTemplate);
        MockitoAnnotations.openMocks(this);
    }

    public void reset() {
        mockServer.reset();
    }

    public void mockCreateProjectForExercise(ProgrammingExercise exercise) throws GitLabApiException {
        final var exercisePath = exercise.getProjectKey();
        final var exerciseName = exercisePath + " " + exercise.getTitle();
        final Group group = new Group().withPath(exercisePath).withName(exerciseName).withVisibility(Visibility.PRIVATE);
        doReturn(group).when(groupApi).addGroup(group);
        mockGetUserID();
        mockAddUserToGroup(exercise.getProjectKey(), MAINTAINER, "instructor1", 2);
        mockAddUserToGroup(exercise.getProjectKey(), GUEST, "tutor1", 3);
    }

    /**
     * Method to mock the getUser method to return mocked users with their id's
     *
     * @throws GitLabApiException in case of git lab api errors
     */
    public void mockGetUserID() throws GitLabApiException {
        User instructor = new User();
        User tutor = new User();
        User user = new User();
        instructor.setId(2);
        tutor.setId(3);
        user.setId(4);

        doReturn(instructor).when(userApi).getUser("instructor1");
        doReturn(tutor).when(userApi).getUser("tutor1");
        doReturn(user).when(userApi).getUser("user1");
    }

    public void mockUpdateUser() throws GitLabApiException {
        doReturn(new org.gitlab4j.api.models.User()).when(userApi).updateUser(any(), any());
    }

    public void mockAddUserToGroup(String group, AccessLevel accessLevel, String userName, Integer userId) throws GitLabApiException {
        final Member member = new Member();
        member.setAccessLevel(accessLevel);
        member.setId(userId);
        member.setName(userName);
        doReturn(member).when(groupApi).addMember(group, userId, accessLevel);
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

    public void mockFailOnGetUserById(String login) throws GitLabApiException {
        UserApi userApi = mock(UserApi.class);
        doReturn(userApi).when(gitLabApi).getUserApi();
        doReturn(null).when(userApi).getUser(eq(login));
    }

    /**
     * Mocks that given user is not found in GitLab and is hence created.
     *
     * @param login Login of the user who's creation is mocked
     * @throws GitLabApiException Never
     */
    public void mockCreationOfUser(String login) throws GitLabApiException {
        UserApi userApi = mock(UserApi.class);
        doReturn(userApi).when(gitLabApi).getUserApi();
        doReturn(null).when(userApi).getUser(eq(login));
        doAnswer(invocation -> {
            User user = (User) invocation.getArguments()[0];
            user.setId(1234);
            return user;
        }).when(userApi).createUser(any(), any(), anyBoolean());
    }

    public void mockCopyRepositoryForParticipation(ProgrammingExercise exercise, String username) throws GitLabApiException {
        final var projectKey = exercise.getProjectKey();
        final var clonedRepoName = projectKey.toLowerCase() + "-" + username.toLowerCase();

        mockCreateRepository(exercise, clonedRepoName);
    }

    public void mockConfigureRepository(ProgrammingExercise exercise, String username, Set<de.tum.in.www1.artemis.domain.User> users, boolean ltiUserExists)
            throws GitLabApiException {
        // TODO: Does it make sense to use the template repository URL here? .configureRepository() is only
        // used by ParticipationService and ProgrammingExerciseParticipationService. At the moment
        // the repositoryUrl that is used when the test runs is different to the one that we mock
        // the Gitlab API methods for. In this case Mockito returns null instead.
        var repositoryUrl = exercise.getVcsTemplateRepositoryUrl();
        for (de.tum.in.www1.artemis.domain.User user : users) {
            String loginName = user.getLogin();
            if ((userPrefixEdx.isPresent() && loginName.startsWith(userPrefixEdx.get())) || (userPrefixU4I.isPresent() && loginName.startsWith((userPrefixU4I.get())))) {
                if (ltiUserExists) {
                    mockUserExists(loginName, true);
                }
                else {
                    mockUserExists(loginName, false);
                    mockImportUser();
                }

            }

            mockAddMemberToRepository(repositoryUrl, user);
        }
        var defaultBranch = "main";
        mockGetDefaultBranch(defaultBranch, repositoryUrl);
        mockProtectBranch(defaultBranch, repositoryUrl);
    }

    private void mockUserExists(String username, boolean exists) throws GitLabApiException {
        doReturn(exists ? new User().withUsername(username) : null).when(userApi).getUser(username);
    }

    private void mockImportUser() throws GitLabApiException {
        doReturn(new User()).when(userApi).createUser(any(), anyString(), anyBoolean());
    }

    private void mockAddMemberToRepository(VcsRepositoryUrl repositoryUrl, de.tum.in.www1.artemis.domain.User user) throws GitLabApiException {
        final var repositoryId = getPathIDFromRepositoryURL(repositoryUrl);
        mockAddMemberToRepository(repositoryId, user);
    }

    public void mockAddMemberToRepository(String repositoryId, de.tum.in.www1.artemis.domain.User user) throws GitLabApiException {
        final var mockedUserId = 1;
        mockGitlabUserManagementServiceGetUserId(user.getLogin(), mockedUserId);
        doReturn(new Member()).when(projectApi).addMember(repositoryId, mockedUserId, DEVELOPER);
    }

    public void mockGetDefaultBranch(String defaultBranch, VcsRepositoryUrl repositoryUrl) throws GitLabApiException {
        // TODO: Use explicit repositoryId when we supply it from the user Participation
        // var repositoryId = getPathIDFromRepositoryURL(repositoryUrl);
        var mockProject = new Project();
        mockProject.setDefaultBranch(defaultBranch);
        doReturn(mockProject).when(projectApi).getProject(notNull());
    }

    private void mockProtectBranch(String branch, VcsRepositoryUrl repositoryUrl) throws GitLabApiException {
        final var repositoryId = getPathIDFromRepositoryURL(repositoryUrl);
        doReturn(new Branch()).when(repositoryApi).unprotectBranch(repositoryId, branch);
        doReturn(new ProtectedBranch()).when(protectedBranchesApi).protectBranch(repositoryId, branch);
    }

    private String getPathIDFromRepositoryURL(VcsRepositoryUrl repository) {
        final var namespaces = repository.toString().split("/");
        final var last = namespaces.length - 1;

        return namespaces[last - 1] + "/" + namespaces[last].replace(".git", "");
    }

    public void mockFailToCheckIfProjectExists(String projectKey) throws GitLabApiException {
        doThrow(GitLabApiException.class).when(projectApi).getProjects(projectKey);
    }

    public void mockHealth(String healthStatus, HttpStatus httpStatus) throws URISyntaxException, JsonProcessingException {
        final var uri = UriComponentsBuilder.fromUri(gitlabServerUrl.toURI()).path("/-/liveness").build().toUri();
        final var response = new ObjectMapper().writeValueAsString(Map.of("status", healthStatus));
        mockServerShortTimeout.expect(requestTo(uri)).andExpect(method(HttpMethod.GET)).andRespond(withStatus(httpStatus).contentType(MediaType.APPLICATION_JSON).body(response));
    }

    public void mockRemoveMemberFromRepository(String repositoryId, de.tum.in.www1.artemis.domain.User user) throws GitLabApiException {
        final var mockedUserId = 1;
        mockGitlabUserManagementServiceGetUserId(user.getLogin(), mockedUserId);
        doNothing().when(projectApi).removeMember(repositoryId, mockedUserId);
    }

    private void mockGitlabUserManagementServiceGetUserId(String username, int userIdToReturn) throws GitLabApiException {
        var gitlabUser = new User().withId(userIdToReturn).withUsername(username);
        doReturn(gitlabUser).when(userApi).getUser(username);
    }
}
