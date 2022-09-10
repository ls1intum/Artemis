package de.tum.in.www1.artemis.connector;

import static org.gitlab4j.api.models.AccessLevel.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.net.URISyntaxException;
import java.net.URL;
import java.time.ZonedDateTime;
import java.util.*;

import org.gitlab4j.api.*;
import org.gitlab4j.api.models.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
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

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.UrlService;
import de.tum.in.www1.artemis.service.connectors.gitlab.GitLabException;
import de.tum.in.www1.artemis.service.connectors.gitlab.GitLabUserDoesNotExistException;
import de.tum.in.www1.artemis.service.connectors.gitlab.GitLabUserManagementService;
import de.tum.in.www1.artemis.service.connectors.gitlab.dto.GitLabPersonalAccessTokenResponseDTO;

@Component
@Profile("gitlab")
public class GitlabRequestMockProvider {

    @Value("${artemis.version-control.default-branch:main}")
    protected String defaultBranch;

    @Value("${artemis.version-control.url}")
    private URL gitlabServerUrl;

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
    private EventsApi eventsApi;

    @Mock
    private RepositoryApi repositoryApi;

    @Mock
    private ProtectedBranchesApi protectedBranchesApi;

    @Mock
    private PipelineApi pipelineApi;

    @SpyBean
    private GitLabUserManagementService gitLabUserManagementService;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UrlService urlService;

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

    /**
     * Verify that the mocked REST-calls were called
     */
    public void verifyMocks() {
        mockServer.verify();
    }

    public void mockCreateProjectForExercise(ProgrammingExercise exercise) throws GitLabApiException {
        final var exercisePath = exercise.getProjectKey();
        final var exerciseName = exercisePath + " " + exercise.getTitle();
        final Group group = new Group().withPath(exercisePath).withName(exerciseName).withVisibility(Visibility.PRIVATE);
        doReturn(group).when(groupApi).addGroup(group);
        mockGetUserID();
        mockAddUserToGroup(exercise.getProjectKey(), MAINTAINER, "instructor1", 2L);
        mockAddUserToGroup(exercise.getProjectKey(), GUEST, "tutor1", 3L);
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
        instructor.setId(2L);
        tutor.setId(3L);
        user.setId(4L);

        doReturn(instructor).when(userApi).getUser("instructor1");
        doReturn(tutor).when(userApi).getUser("tutor1");
        doReturn(user).when(userApi).getUser("user1");
    }

    public void mockUpdateUser() throws GitLabApiException {
        doReturn(new org.gitlab4j.api.models.User()).when(userApi).updateUser(any(), any());
    }

    public void mockAddUserToGroup(String group, AccessLevel accessLevel, String userName, Long userId) throws GitLabApiException {
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
        group.setId(1L);
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

    /**
     * Mocks the call on the events API to receive all qualifying push events to get the push dates of certain commits.
     *
     * @param participation         Affected participation
     * @param commitHashPushDateMap A map mapping the commit hashes to their push date. We expect here that only one commit is pushed at a time and the order of the map is the
     *                              order of the commits
     * @throws GitLabApiException if events API fails
     */
    public void mockGetPushDate(ProgrammingExerciseParticipation participation, Map<String, ZonedDateTime> commitHashPushDateMap) throws GitLabApiException {
        if (commitHashPushDateMap.isEmpty()) {
            return;
        }
        List<String> commits = new ArrayList<>(commitHashPushDateMap.keySet());
        commits.add(0, "7".repeat(40));
        List<Event> events = new ArrayList<>();
        for (int i = 0; i < commits.size() - 1; i++) {
            PushData pushData = new PushData();
            pushData.setAction(Constants.ActionType.PUSHED);
            pushData.setCommitCount(1);
            pushData.setCommitFrom(commits.get(i));
            pushData.setCommitTo(commits.get(i + 1));
            Event event = new Event().withCreatedAt(Date.from(commitHashPushDateMap.get(commits.get(i + 1)).toInstant()));
            event.setPushData(pushData);
            events.add(0, event); // The latest event has to be at the front
        }
        var path = urlService.getRepositoryPathFromRepositoryUrl(participation.getVcsRepositoryUrl());
        doAnswer((invocation) -> events.stream()).when(eventsApi).getProjectEventsStream(eq(path), eq(Constants.ActionType.PUSHED), eq(null), eq(null), eq(null),
                eq(Constants.SortOrder.DESC));
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
    public void mockCreationOfUser(String login) throws GitLabApiException, JsonProcessingException {
        var userId = 1234L;
        UserApi userApi = mock(UserApi.class);
        doReturn(userApi).when(gitLabApi).getUserApi();
        doReturn(null).when(userApi).getUser(eq(login));
        doAnswer(invocation -> {
            User user = (User) invocation.getArguments()[0];
            user.setId(userId);
            return user;
        }).when(userApi).createUser(any(), any(), anyBoolean());

        var accessTokenResponseDTO = new GitLabPersonalAccessTokenResponseDTO();
        accessTokenResponseDTO.setName("acccess-token-name");
        accessTokenResponseDTO.setToken("acccess-token-value");
        accessTokenResponseDTO.setUserId(userId);
        final var response = new ObjectMapper().writeValueAsString(accessTokenResponseDTO);

        mockServer.expect(requestTo(gitLabApi.getGitLabServerUrl() + "/api/v4/users/" + userId + "/personal_access_tokens")).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(response));
    }

    public void mockCopyRepositoryForParticipation(ProgrammingExercise exercise, String username) throws GitLabApiException {
        final var projectKey = exercise.getProjectKey();
        final var clonedRepoName = projectKey.toLowerCase() + "-" + username.toLowerCase();

        mockCreateRepository(exercise, clonedRepoName);
    }

    public void mockConfigureRepository(ProgrammingExercise exercise, Set<de.tum.in.www1.artemis.domain.User> users, boolean userExists) throws GitLabApiException {
        var repositoryUrl = exercise.getVcsTemplateRepositoryUrl();
        for (var user : users) {
            String loginName = user.getLogin();
            mockUserExists(loginName, userExists);
            if (userExists) {
                mockAddMemberToRepository(repositoryUrl, user.getLogin());
            }
        }
        mockGetDefaultBranch(defaultBranch);
        mockProtectBranch(defaultBranch, repositoryUrl);
    }

    private void mockUserExists(String username, boolean exists) throws GitLabApiException {
        doReturn(exists ? new User().withUsername(username) : null).when(userApi).getUser(username);
    }

    private void mockImportUser(de.tum.in.www1.artemis.domain.User user, boolean shouldFail) throws GitLabApiException {
        final var gitlabUser = new org.gitlab4j.api.models.User().withEmail(user.getEmail()).withUsername(user.getLogin()).withName(user.getName()).withCanCreateGroup(false)
                .withCanCreateProject(false).withSkipConfirmation(true);
        if (!shouldFail) {
            var createdUser = gitlabUser.withId(1L);
            doReturn(createdUser).when(userApi).createUser(isA(User.class), anyString(), eq(false));
        }
        else {
            doThrow(GitLabApiException.class).when(userApi).createUser(isA(User.class), anyString(), eq(false));
        }
    }

    private void mockAddMemberToRepository(VcsRepositoryUrl repositoryUrl, String login) throws GitLabApiException {
        final var repositoryPath = urlService.getRepositoryPathFromRepositoryUrl(repositoryUrl);
        mockAddMemberToRepository(repositoryPath, login, false);
    }

    public void mockAddMemberToRepository(String repositoryPath, String login, boolean throwError) throws GitLabApiException {
        final var mockedUserId = 1L;
        doReturn(mockedUserId).when(gitLabUserManagementService).getUserId(login);
        if (throwError) {
            System.out.println("repositoryPath: " + repositoryPath + ", mockedUserId: " + mockedUserId);
            doThrow(new GitLabApiException("Bad Request", 400)).when(projectApi).addMember(repositoryPath, mockedUserId, DEVELOPER);
        }
        else {
            doReturn(new Member()).when(projectApi).addMember(repositoryPath, mockedUserId, DEVELOPER);
        }
    }

    public void mockGetDefaultBranch(String defaultBranch) throws GitLabApiException {
        var mockProject = new Project();
        mockProject.setDefaultBranch(defaultBranch);
        doReturn(mockProject).when(projectApi).getProject(notNull());
    }

    private void mockProtectBranch(String branch, VcsRepositoryUrl repositoryUrl) throws GitLabApiException {
        final var repositoryPath = urlService.getRepositoryPathFromRepositoryUrl(repositoryUrl);
        doReturn(new Branch()).when(repositoryApi).unprotectBranch(repositoryPath, branch);
        doReturn(new ProtectedBranch()).when(protectedBranchesApi).protectBranch(repositoryPath, branch);
    }

    public void mockFailToCheckIfProjectExists(String projectKey) throws GitLabApiException {
        doThrow(GitLabApiException.class).when(projectApi).getProjects(projectKey);
    }

    public void mockHealth(String healthStatus, HttpStatus httpStatus) throws URISyntaxException, JsonProcessingException {
        final var uri = UriComponentsBuilder.fromUri(gitlabServerUrl.toURI()).path("/-/liveness").build().toUri();
        final var response = new ObjectMapper().writeValueAsString(Map.of("status", healthStatus));
        mockServerShortTimeout.expect(requestTo(uri)).andExpect(method(HttpMethod.GET)).andRespond(withStatus(httpStatus).contentType(MediaType.APPLICATION_JSON).body(response));
    }

    public void mockRemoveMemberFromRepository(String repositoryPath, String login) throws GitLabApiException {
        final var mockedUserId = 1L;
        doReturn(mockedUserId).when(gitLabUserManagementService).getUserId(login);
        doNothing().when(projectApi).removeMember(repositoryPath, mockedUserId);
    }

    public void mockUpdateVcsUser(String login, de.tum.in.www1.artemis.domain.User user, Set<String> removedGroups, Set<String> addedGroups, boolean shouldSynchronizePassword)
            throws GitLabApiException {
        mockUpdateBasicUserInformation(login, user, shouldSynchronizePassword);
        mockUpdateUserActivationState(user, false);

        // Add as member to new groups
        if (addedGroups != null && !addedGroups.isEmpty()) {
            final var exercisesWithAddedGroups = programmingExerciseRepository.findAllByInstructorOrEditorOrTAGroupNameIn(addedGroups);
            for (final var exercise : exercisesWithAddedGroups) {
                final var accessLevel = addedGroups.contains(exercise.getCourseViaExerciseGroupOrCourseMember().getInstructorGroupName()) ? MAINTAINER : GUEST;
                doReturn(new Member()).when(groupApi).addMember(eq(exercise.getProjectKey()), anyLong(), eq(accessLevel));
            }
        }

        // Update/remove old groups
        if (removedGroups != null && !removedGroups.isEmpty()) {
            final var exercisesWithOutdatedGroups = programmingExerciseRepository.findAllByInstructorOrEditorOrTAGroupNameIn(removedGroups);
            for (final var exercise : exercisesWithOutdatedGroups) {
                // If the user is still in another group for the exercise (TA -> INSTRUCTOR or INSTRUCTOR -> TA),
                // then we have to add him as a member with the new access level
                final var course = exercise.getCourseViaExerciseGroupOrCourseMember();
                if (user.getGroups().contains(course.getInstructorGroupName())) {
                    doReturn(new Member()).when(groupApi).updateMember(eq(exercise.getProjectKey()), anyLong(), eq(MAINTAINER));
                }
                else if (user.getGroups().contains(course.getTeachingAssistantGroupName())) {
                    doReturn(new Member()).when(groupApi).updateMember(eq(exercise.getProjectKey()), anyLong(), eq(GUEST));
                }
                else {
                    // If the user is not a member of any relevant group anymore, we can remove him from the exercise
                    doNothing().when(groupApi).removeMember(eq(exercise.getProjectKey()), anyLong());
                }
            }
        }
    }

    public void mockUpdateVcsUserFailToActivate(String login, de.tum.in.www1.artemis.domain.User user) throws GitLabApiException {
        mockUpdateBasicUserInformation(login, user, true);
        mockUpdateUserActivationState(user, true);
    }

    public void mockUpdateBasicUserInformation(String login, de.tum.in.www1.artemis.domain.User user, boolean shouldUpdatePassword) throws GitLabApiException {
        var gitlabUser = new User().withUsername(login).withId(1L);
        doReturn(gitlabUser).when(userApi).getUser(login);
        if (shouldUpdatePassword) {
            doReturn(gitlabUser).when(userApi).updateUser(eq(gitlabUser), any(CharSequence.class));
        }
        else {
            doReturn(gitlabUser).when(userApi).updateUser(gitlabUser, null);
        }
    }

    public void mockRemoveUserFromGroup(Long gitlabUserId, String group, Optional<GitLabApiException> exceptionToThrow) throws GitLabApiException {
        var exercises = programmingExerciseRepository.findAllByInstructorOrEditorOrTAGroupNameIn(Set.of(group));
        for (var exercise : exercises) {
            if (exceptionToThrow.isEmpty()) {
                doNothing().when(groupApi).removeMember(exercise.getProjectKey(), gitlabUserId);
            }
            else {
                doThrow(exceptionToThrow.get()).when(groupApi).removeMember(exercise.getProjectKey(), gitlabUserId);
            }
        }
    }

    public void mockDeleteVcsUser(String login, boolean userExists, boolean shouldFailToDelete) throws GitLabApiException {
        mockGetUserId(login, true, false);
        if (!userExists) {
            doThrow(GitLabUserDoesNotExistException.class).when(userApi).deleteUser(anyLong(), eq(true));
        }
        else if (shouldFailToDelete) {
            doThrow(GitLabApiException.class).when(userApi).deleteUser(anyLong(), eq(true));
        }
        else {
            doNothing().when(userApi).deleteUser(anyLong(), eq(true));
        }
    }

    public void mockDeleteVcsUserFailToGetUserId(String login) throws GitLabApiException {
        mockGetUserId(login, true, true);
    }

    public void mockGetUserId(String username, boolean userExists, boolean shouldFail) throws GitLabApiException {
        if (shouldFail) {
            doThrow(GitLabApiException.class).when(userApi).getUser(username);
        }
        else if (userExists) {
            doReturn(new User().withId(1L)).when(userApi).getUser(username);
        }
        else {
            throw new GitLabUserDoesNotExistException(username);
        }
    }

    public void mockCreateVcsUser(de.tum.in.www1.artemis.domain.User user, boolean shouldFail) throws GitLabApiException {
        var userId = mockGetUserIdCreateIfNotExist(user, false, shouldFail);

        // Add user to existing exercises
        if (user.getGroups() != null && !user.getGroups().isEmpty()) {
            final var instructorExercises = programmingExerciseRepository.findAllByCourse_InstructorGroupNameIn(user.getGroups());
            final var editorExercises = programmingExerciseRepository.findAllByCourse_EditorGroupNameIn(user.getGroups()).stream()
                    .filter(programmingExercise -> !instructorExercises.contains(programmingExercise)).toList();
            final var teachingAssistantExercises = programmingExerciseRepository.findAllByCourse_TeachingAssistantGroupNameIn(user.getGroups()).stream()
                    .filter(programmingExercise -> !instructorExercises.contains(programmingExercise)).toList();
            mockAddUserToGroups(userId, instructorExercises, MAINTAINER);
            mockAddUserToGroups(userId, editorExercises, DEVELOPER);
            mockAddUserToGroups(userId, teachingAssistantExercises, GUEST);
        }
    }

    public void mockAddUserToGroupsUserExists(de.tum.in.www1.artemis.domain.User user, String projectKey) throws GitLabApiException {
        Long userId = mockGetUserIdCreateIfNotExist(user, false, false);
        doThrow(new GitLabApiException("Member already exists")).when(groupApi).addMember(eq(projectKey), eq(userId), any(AccessLevel.class));
    }

    public void mockAddUserToGroupsFails(de.tum.in.www1.artemis.domain.User user, String projectKey) throws GitLabApiException {
        Long userId = mockGetUserIdCreateIfNotExist(user, false, false);
        doThrow(new GitLabApiException("Oh no")).when(groupApi).addMember(eq(projectKey), eq(userId), any(AccessLevel.class));
    }

    private Long mockGetUserIdCreateIfNotExist(de.tum.in.www1.artemis.domain.User user, boolean userExists, boolean shouldFail) throws GitLabApiException {
        var userToReturn = new User().withId(1L).withUsername(user.getLogin());
        doReturn(userExists ? userToReturn : null).when(userApi).getUser(user.getLogin());
        if (!userExists) {
            mockImportUser(user, shouldFail);
        }
        return userToReturn.getId();
    }

    private void mockAddUserToGroups(Long userId, List<ProgrammingExercise> exercises, AccessLevel accessLevel) throws GitLabApiException {
        for (final var exercise : exercises) {
            doReturn(new Member()).when(groupApi).addMember(exercise.getProjectKey(), userId, accessLevel);
        }
    }

    public void mockUpdateCoursePermissions(Course updatedCourse, String oldInstructorGroup, String oldEditorGroup, String oldTeachingAssistantGroup) throws GitLabApiException {
        if (oldInstructorGroup.equals(updatedCourse.getInstructorGroupName()) && oldEditorGroup.equals(updatedCourse.getEditorGroupName())
                && oldTeachingAssistantGroup.equals(updatedCourse.getTeachingAssistantGroupName())) {
            // Do nothing if the group names didn't change
            return;
        }

        final List<ProgrammingExercise> programmingExercises = programmingExerciseRepository.findAllProgrammingExercisesInCourseOrInExamsOfCourse(updatedCourse);

        final var allUsers = userRepository.findAllInGroupWithAuthorities(oldInstructorGroup);
        allUsers.addAll(userRepository.findAllInGroupWithAuthorities(oldEditorGroup));
        allUsers.addAll(userRepository.findAllInGroupWithAuthorities(oldTeachingAssistantGroup));
        allUsers.addAll(userRepository.findAllUserInGroupAndNotIn(updatedCourse.getInstructorGroupName(), allUsers));
        allUsers.addAll(userRepository.findAllUserInGroupAndNotIn(updatedCourse.getEditorGroupName(), allUsers));
        allUsers.addAll(userRepository.findAllUserInGroupAndNotIn(updatedCourse.getTeachingAssistantGroupName(), allUsers));

        final Set<de.tum.in.www1.artemis.domain.User> oldUsers = new HashSet<>();
        final Set<de.tum.in.www1.artemis.domain.User> newUsers = new HashSet<>();

        for (var user : allUsers) {
            Set<String> userGroups = user.getGroups();
            if (userGroups.contains(oldTeachingAssistantGroup) || userGroups.contains(oldEditorGroup) || userGroups.contains(oldInstructorGroup)) {
                oldUsers.add(user);
            }
            else {
                newUsers.add(user);
            }
        }

        mockUpdateOldGroupMembers(programmingExercises, oldUsers, updatedCourse);
        mockSetPermissionsForNewGroupMembers(programmingExercises, newUsers, updatedCourse);
    }

    private void mockUpdateOldGroupMembers(List<ProgrammingExercise> programmingExercises, Set<de.tum.in.www1.artemis.domain.User> oldUsers, Course updatedCourse)
            throws GitLabApiException {
        for (var user : oldUsers) {
            mockGetUserId(user.getLogin(), true, false);

            Set<String> groups = user.getGroups();
            if (groups == null) {
                mockRemoveMemberFromExercises(programmingExercises);
                continue;
            }

            Optional<AccessLevel> accessLevel = getAccessLevelFromUserGroups(groups, updatedCourse);
            if (accessLevel.isPresent()) {
                mockUpdateMemberExercisePermissions(programmingExercises, accessLevel.get());
            }
            else {
                mockRemoveMemberFromExercises(programmingExercises);
            }
        }
    }

    private void mockSetPermissionsForNewGroupMembers(List<ProgrammingExercise> programmingExercises, Set<de.tum.in.www1.artemis.domain.User> newUsers, Course updatedCourse) {
        for (de.tum.in.www1.artemis.domain.User user : newUsers) {
            try {
                mockGetUserId(user.getLogin(), true, false);

                Optional<AccessLevel> accessLevel = getAccessLevelFromUserGroups(user.getGroups(), updatedCourse);
                if (accessLevel.isPresent()) {
                    mockAddUserToGroups(1L, programmingExercises, accessLevel.get());
                }
                else {
                    mockRemoveMemberFromExercises(programmingExercises);
                }
            }
            catch (GitLabApiException e) {
                throw new GitLabException("Error while trying to set permission for user in GitLab: " + user, e);
            }
        }
    }

    private Optional<AccessLevel> getAccessLevelFromUserGroups(Set<String> userGroups, Course course) {
        String instructorGroup = course.getInstructorGroupName();
        String editorGroup = course.getEditorGroupName();
        String teachingAssistantGroup = course.getTeachingAssistantGroupName();

        if (userGroups.contains(instructorGroup)) {
            return Optional.of(MAINTAINER);
        }
        else if (userGroups.contains(editorGroup)) {
            return Optional.of(DEVELOPER);
        }
        else if (userGroups.contains(teachingAssistantGroup)) {
            return Optional.of(REPORTER);
        }
        else {
            return Optional.empty();
        }
    }

    private void mockUpdateMemberExercisePermissions(List<ProgrammingExercise> programmingExercises, AccessLevel accessLevel) throws GitLabApiException {
        for (var exercise : programmingExercises) {
            doReturn(new Member()).when(groupApi).updateMember(eq(exercise.getProjectKey()), anyLong(), eq(accessLevel));
        }
    }

    private void mockRemoveMemberFromExercises(List<ProgrammingExercise> programmingExercises) throws GitLabApiException {
        for (var exercise : programmingExercises) {
            doNothing().when(groupApi).removeMember(eq(exercise.getProjectKey()), anyLong());
        }
    }

    public void mockFailToGetUserWhenUpdatingOldMembers(de.tum.in.www1.artemis.domain.User user) throws GitLabApiException {
        mockGetUserId(user.getLogin(), false, true);
    }

    public void mockFailToUpdateOldGroupMembers(ProgrammingExercise exercise, de.tum.in.www1.artemis.domain.User user) throws GitLabApiException {
        mockGetUserId(user.getLogin(), true, false);
        doThrow(GitLabApiException.class).when(groupApi).updateMember(eq(exercise.getProjectKey()), eq(1L), any(AccessLevel.class));
    }

    public void mockFailToRemoveOldMember(ProgrammingExercise programmingExercise, de.tum.in.www1.artemis.domain.User user) throws GitLabApiException {
        mockGetUserId(user.getLogin(), true, false);
        doThrow(GitLabApiException.class).when(groupApi).removeMember(programmingExercise.getProjectKey(), 1L);
    }

    public void mockDeleteRepository(String repositoryPath, boolean shouldFail) throws GitLabApiException {
        if (shouldFail) {
            doThrow(new GitLabApiException("Bad Request", 400)).when(projectApi).deleteProject(repositoryPath);
        }
        else {
            doNothing().when(projectApi).deleteProject(repositoryPath);
        }
    }

    public void mockDeleteProject(String projectKey, boolean shouldFail) throws GitLabApiException {
        if (shouldFail) {
            doThrow(new GitLabApiException("Bad request", 400)).when(groupApi).deleteGroup(projectKey);
        }
        else {
            doNothing().when(groupApi).deleteGroup(projectKey);
        }
    }

    public void mockRepositoryUrlIsValid(VcsRepositoryUrl repositoryUrl, boolean isUrlValid) throws GitLabApiException {
        if (repositoryUrl == null || repositoryUrl.getURI() == null) {
            return;
        }

        final var repositoryPath = urlService.getRepositoryPathFromRepositoryUrl(repositoryUrl);
        if (isUrlValid) {
            doReturn(new Project()).when(projectApi).getProject(repositoryPath);
        }
        else {
            when(projectApi.getProject(repositoryPath)).thenAnswer(invocation -> {
                throw new Exception("exception");
            });
        }
    }

    public void setRepositoryPermissionsToReadOnly(VcsRepositoryUrl repositoryUrl, Set<de.tum.in.www1.artemis.domain.User> users) throws GitLabApiException {
        for (var user : users) {
            mockGetUserId(user.getLogin(), true, false);
            final var repositoryPath = urlService.getRepositoryPathFromRepositoryUrl(repositoryUrl);
            doReturn(new Member()).when(projectApi).updateMember(repositoryPath, 1L, GUEST);
        }
    }

    public void mockDeactivateUser(String userLogin, boolean shouldFail) throws GitLabApiException {
        mockGetUserId(userLogin, true, false);
        mockBlockUser(shouldFail);
    }

    public void mockActivateUser(String userLogin, boolean shouldFail) throws GitLabApiException {
        mockGetUserId(userLogin, true, false);
        mockUnblockUser(shouldFail);
    }

    public void mockUpdateUserActivationState(de.tum.in.www1.artemis.domain.User user, boolean shouldFail) throws GitLabApiException {
        if (user.getActivated()) {
            mockUnblockUser(shouldFail);
        }
        else {
            mockBlockUser(shouldFail);
        }
    }

    public void mockBlockUser(boolean shouldFail) throws GitLabApiException {
        if (shouldFail) {
            doThrow(new GitLabApiException("Internal Error", 500)).when(userApi).blockUser(anyLong());
        }
        else {
            doNothing().when(userApi).blockUser(anyLong());
        }
    }

    public void mockUnblockUser(boolean shouldFail) throws GitLabApiException {
        if (shouldFail) {
            doThrow(new GitLabApiException("Internal Error", 500)).when(userApi).unblockUser(anyLong());
        }
        else {
            doNothing().when(userApi).blockUser(anyLong());
        }
    }

    public UserApi getMockedUserApi() {
        return userApi;
    }

    public void mockCreateTrigger(boolean shouldFail) throws GitLabApiException {
        if (shouldFail) {
            doThrow(new GitLabApiException("Internal Error", 500)).when(pipelineApi).createPipelineTrigger(any(), anyString());
        }
        else {
            doReturn(new Trigger()).when(pipelineApi).createPipelineTrigger(any(), anyString());
        }
    }

    public void mockTriggerPipeline(boolean shouldFail) throws GitLabApiException {
        if (shouldFail) {
            doThrow(new GitLabApiException("Internal Error", 500)).when(pipelineApi).triggerPipeline(any(), (Trigger) any(), anyString(), any());
        }
        else {
            doReturn(null).when(pipelineApi).triggerPipeline(any(), (Trigger) any(), anyString(), any());
        }
    }

    public void mockDeleteTrigger(boolean shouldFail) throws GitLabApiException {
        if (shouldFail) {
            doThrow(new GitLabApiException("Internal Error", 500)).when(pipelineApi).deletePipelineTrigger(any(), anyLong());
        }
        else {
            doNothing().when(pipelineApi).deletePipelineTrigger(any(), anyLong());
        }
    }

    public void mockGetProject(boolean shouldFail) throws GitLabApiException {
        if (shouldFail) {
            doThrow(new GitLabApiException("Internal Error", 500)).when(projectApi).getProject(anyString());
        }
        else {
            Project project = new Project();
            doReturn(project).when(projectApi).getProject(anyString());
        }
    }

    public void mockUpdateProject(boolean shouldFail) throws GitLabApiException {
        if (shouldFail) {
            doThrow(new GitLabApiException("Internal Error", 500)).when(projectApi).updateProject(any());
        }
        else {
            Project project = new Project();
            doReturn(project).when(projectApi).updateProject(any());
        }
    }

    public void mockGetBuildStatus(PipelineStatus pipelineStatus) throws GitLabApiException {
        List<Pipeline> pipelines = new ArrayList<>();
        Pipeline pipeline = new Pipeline();
        pipeline.setStatus(pipelineStatus);
        pipelines.add(pipeline);

        doReturn(pipelines).when(pipelineApi).getPipelines(anyString());
    }
}
