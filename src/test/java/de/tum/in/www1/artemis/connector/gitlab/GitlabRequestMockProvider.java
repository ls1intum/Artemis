package de.tum.in.www1.artemis.connector.gitlab;

import static org.gitlab4j.api.models.AccessLevel.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

import org.gitlab4j.api.*;
import org.gitlab4j.api.models.*;
import org.gitlab4j.api.models.User;
import org.mockito.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.connectors.gitlab.GitLabUserDoesNotExistException;
import de.tum.in.www1.artemis.service.connectors.gitlab.GitLabUserManagementService;
import de.tum.in.www1.artemis.service.user.PasswordService;

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

    @SpyBean
    private PasswordService passwordService;

    @SpyBean
    private GitLabUserManagementService gitLabUserManagementService;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private UserRepository userRepository;

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
        var repositoryUrl = exercise.getVcsTemplateRepositoryUrl();
        for (de.tum.in.www1.artemis.domain.User user : users) {
            String loginName = user.getLogin();
            if ((userPrefixEdx.isPresent() && loginName.startsWith(userPrefixEdx.get())) || (userPrefixU4I.isPresent() && loginName.startsWith((userPrefixU4I.get())))) {
                mockUserExists(loginName, ltiUserExists);
                if (!ltiUserExists) {
                    mockImportUser(user, false);
                }
            }

            mockAddMemberToRepository(repositoryUrl, user);
        }
        mockProtectBranch("master", repositoryUrl);
    }

    private void mockUserExists(String username, boolean exists) throws GitLabApiException {
        doReturn(exists ? new User().withUsername(username) : null).when(userApi).getUser(username);
    }

    private void mockImportUser(de.tum.in.www1.artemis.domain.User user, boolean shouldFail) throws GitLabApiException {
        final var gitlabUser = new org.gitlab4j.api.models.User().withEmail(user.getEmail()).withUsername(user.getLogin()).withName(user.getName()).withCanCreateGroup(false)
                .withCanCreateProject(false).withSkipConfirmation(true);
        doReturn(user.getPassword()).when(passwordService).decryptPassword(user);

        if (!shouldFail) {
            var createdUser = gitlabUser.withId(1);
            doReturn(createdUser).when(userApi).createUser(isA(User.class), anyString(), eq(false));
        }
        else {
            doThrow(GitLabApiException.class).when(userApi).createUser(isA(User.class), anyString(), eq(false));
        }
    }

    private void mockAddMemberToRepository(VcsRepositoryUrl repositoryUrl, de.tum.in.www1.artemis.domain.User user) throws GitLabApiException {
        final var repositoryId = getPathIDFromRepositoryURL(repositoryUrl);
        mockAddMemberToRepository(repositoryId, user);
    }

    public void mockAddMemberToRepository(String repositoryId, de.tum.in.www1.artemis.domain.User user) throws GitLabApiException {
        final var mockedUserId = 1;
        doReturn(mockedUserId).when(gitLabUserManagementService).getUserId(user.getLogin());
        doReturn(new Member()).when(projectApi).addMember(repositoryId, mockedUserId, DEVELOPER);
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
        doReturn(mockedUserId).when(gitLabUserManagementService).getUserId(user.getLogin());
        doNothing().when(projectApi).removeMember(repositoryId, mockedUserId);
    }

    public void mockUpdateVcsUser(String login, de.tum.in.www1.artemis.domain.User user, Set<String> removedGroups, Set<String> addedGroups, boolean shouldSynchronizePassword)
            throws GitLabApiException {
        var gitlabUser = new User().withUsername(login);
        doReturn(gitlabUser).when(userApi).getUser(login);
        if (shouldSynchronizePassword) {
            doReturn(gitlabUser).when(userApi).updateUser(gitlabUser, user.getPassword());
        }
        else {
            doReturn(gitlabUser).when(userApi).updateUser(gitlabUser, null);
        }

        // Add as member to new groups
        if (addedGroups != null && !addedGroups.isEmpty()) {
            final var exercisesWithAddedGroups = programmingExerciseRepository.findAllByInstructorOrTAGroupNameIn(addedGroups);
            for (final var exercise : exercisesWithAddedGroups) {
                final var accessLevel = addedGroups.contains(exercise.getCourseViaExerciseGroupOrCourseMember().getInstructorGroupName()) ? MAINTAINER : GUEST;
                doReturn(new Member()).when(groupApi).addMember(eq(exercise.getProjectKey()), anyInt(), eq(accessLevel));
            }
        }

        // Update/remove old groups
        if (removedGroups != null && !removedGroups.isEmpty()) {
            final var exercisesWithOutdatedGroups = programmingExerciseRepository.findAllByInstructorOrTAGroupNameIn(removedGroups);
            for (final var exercise : exercisesWithOutdatedGroups) {
                // If the the user is still in another group for the exercise (TA -> INSTRUCTOR or INSTRUCTOR -> TA),
                // then we have to add him as a member with the new access level
                final var course = exercise.getCourseViaExerciseGroupOrCourseMember();
                if (user.getGroups().contains(course.getInstructorGroupName())) {
                    doReturn(new Member()).when(groupApi).updateMember(eq(exercise.getProjectKey()), anyInt(), eq(MAINTAINER));
                }
                else if (user.getGroups().contains(course.getTeachingAssistantGroupName())) {
                    doReturn(new Member()).when(groupApi).updateMember(eq(exercise.getProjectKey()), anyInt(), eq(GUEST));
                }
                else {
                    // If the user is not a member of any relevant group any more, we can remove him from the exercise
                    doNothing().when(groupApi).removeMember(eq(exercise.getProjectKey()), anyInt());
                }
            }
        }
    }

    public void mockDeleteVcsUser(String login, boolean shouldFailToDelete) throws GitLabApiException {
        mockGetUserId(login, true);
        if (shouldFailToDelete) {
            doThrow(GitLabApiException.class).when(userApi).deleteUser(anyInt(), eq(true));
        }
        else {
            doNothing().when(userApi).deleteUser(anyInt(), eq(true));
        }
    }

    private void mockGetUserId(String username, boolean userExists) throws GitLabApiException {
        if (userExists) {
            doReturn(new User().withId(1)).when(userApi).getUser(username);
        }
        else {
            throw new GitLabUserDoesNotExistException(username);
        }
    }

    public void mockCreateVcsUser(de.tum.in.www1.artemis.domain.User user, boolean shouldFail) throws GitLabApiException {
        var userId = mockGetUserIdCreateIfNotExist(user, false, shouldFail);

        // Add user to existing exercises
        if (user.getGroups() != null && user.getGroups().size() > 0) {
            final var instructorExercises = programmingExerciseRepository.findAllByCourse_InstructorGroupNameIn(user.getGroups());
            final var teachingAssistantExercises = programmingExerciseRepository.findAllByCourse_TeachingAssistantGroupNameIn(user.getGroups()).stream()
                    .filter(programmingExercise -> !instructorExercises.contains(programmingExercise)).collect(Collectors.toList());
            mockAddUserToGroups(userId, instructorExercises, MAINTAINER);
            mockAddUserToGroups(userId, teachingAssistantExercises, GUEST);
        }
    }

    private int mockGetUserIdCreateIfNotExist(de.tum.in.www1.artemis.domain.User user, boolean userExists, boolean shouldFail) throws GitLabApiException {
        var userToReturn = new User().withId(1).withUsername(user.getLogin());
        doReturn(userExists ? userToReturn : null).when(userApi).getUser(user.getLogin());
        if (!userExists) {
            mockImportUser(user, shouldFail);
        }
        return userToReturn.getId();
    }

    private void mockAddUserToGroups(int userId, List<ProgrammingExercise> exercises, AccessLevel accessLevel) throws GitLabApiException {
        for (final var exercise : exercises) {
            doReturn(new Member()).when(groupApi).addMember(exercise.getProjectKey(), userId, accessLevel);
        }
    }

    public void mockUpdateCoursePermissions(Course updatedCourse, String oldInstructorGroup, String oldTeachingAssistantGroup) throws GitLabApiException {
        if (oldInstructorGroup.equals(updatedCourse.getInstructorGroupName()) && oldTeachingAssistantGroup.equals(updatedCourse.getTeachingAssistantGroupName())) {
            // Do nothing if the group names didn't change
            return;
        }

        final var exercises = programmingExerciseRepository.findAllByCourse(updatedCourse);
        // All users that we already updated

        // Update the old instructors of the course
        final var oldInstructors = userRepository.findAllInGroupWithAuthorities(oldInstructorGroup);
        // doUpgrade=false, because these users already are instructors.
        mockUpdateOldGroupMembers(exercises, oldInstructors, updatedCourse.getInstructorGroupName(), updatedCourse.getTeachingAssistantGroupName(), GUEST, false);
        final var processedUsers = new HashSet<>(oldInstructors);

        // Update the old teaching assistant of the group
        final var oldTeachingAssistants = userRepository.findAllUserInGroupAndNotIn(oldTeachingAssistantGroup, oldInstructors);
        // doUpgrade=true, because these users should be upgraded from TA to instructor, if possible.
        mockUpdateOldGroupMembers(exercises, oldTeachingAssistants, updatedCourse.getTeachingAssistantGroupName(), updatedCourse.getInstructorGroupName(), MAINTAINER, true);
        processedUsers.addAll(oldTeachingAssistants);

        // Now, we only have to add all users that have not been updated yet AND that are part of one of the new groups
        // Find all NEW instructors, that did not belong to the old TAs or instructors
        final var remainingInstructors = userRepository.findAllUserInGroupAndNotIn(updatedCourse.getInstructorGroupName(), processedUsers);
        for (var user : remainingInstructors) {
            mockGetUserId(user.getLogin(), true);
            mockAddUserToGroups(1, exercises, MAINTAINER);
        }
        processedUsers.addAll(remainingInstructors);

        // Find all NEW TAs that did not belong to the old TAs or instructors
        final var remainingTeachingAssistants = userRepository.findAllUserInGroupAndNotIn(updatedCourse.getTeachingAssistantGroupName(), processedUsers);
        for (var user : remainingTeachingAssistants) {
            mockGetUserId(user.getLogin(), true);
            mockAddUserToGroups(1, exercises, GUEST);
        }
    }

    private void mockUpdateOldGroupMembers(List<ProgrammingExercise> exercises, List<de.tum.in.www1.artemis.domain.User> users, String newGroupName, String alternativeGroupName,
            AccessLevel alternativeAccessLevel, boolean doUpgrade) throws GitLabApiException {
        for (final var user : users) {
            mockGetUserId(user.getLogin(), true);
            final var userId = 1;
            /*
             * Contains the access level of the other group, to which the user currently does NOT belong, IF the user could be in that group E.g. user1(groups=[foo,bar]),
             * oldInstructorGroup=foo, oldTAGroup=bar; newInstructorGroup=instr newTAGroup=bar So, while the instructor group changed, the TA group stayed the same. user1 was part
             * of the old instructor group, but isn't any more. BUT he could be a TA according to the new groups, so the alternative access level would be the level of the TA
             * group, i.e. GUEST
             */
            final Optional<AccessLevel> newAccessLevel;
            if (user.getGroups().contains(alternativeGroupName)) {
                newAccessLevel = Optional.of(alternativeAccessLevel);
            }
            else {
                // No alternative access level, if the user does not belong to ANY of the new groups (i.e. TA or instructor)
                newAccessLevel = Optional.empty();
            }
            // The user still is in the TA or instructor group
            final var userStillInRelevantGroup = user.getGroups().contains(newGroupName);
            // We cannot upgrade the user (i.e. from TA to instructor) if the alternative group would be below the current
            // one (i.e. instructor down to TA), or if the user is not eligible for the new access level:
            // TA to instructor, BUT the user does not belong to the new instructor group.
            final var cannotUpgrade = !doUpgrade || newAccessLevel.isEmpty();
            if (userStillInRelevantGroup && cannotUpgrade) {
                continue;
            }

            for (var exercise : exercises) {
                if (newAccessLevel.isPresent()) {
                    doReturn(new Member()).when(groupApi).updateMember(exercise.getProjectKey(), userId, newAccessLevel.get());
                }
                else {
                    // Remove the user from the all groups, if he no longer is a TA, or instructor
                    doNothing().when(groupApi).removeMember(exercise.getProjectKey(), userId);
                }
            }
        }
    }

    public void mockDeleteRepository(String repositoryId, boolean shouldFail) throws GitLabApiException {
        if (shouldFail) {
            doThrow(new GitLabApiException("Bad Request", 400)).when(projectApi).deleteProject(repositoryId);
        }
        else {
            doNothing().when(projectApi).deleteProject(repositoryId);
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
        if (repositoryUrl == null || repositoryUrl.getURL() == null) {
            return;
        }

        final var repositoryId = getPathIDFromRepositoryURL(repositoryUrl);
        if (isUrlValid) {
            doReturn(new Project()).when(projectApi).getProject(repositoryId);
        }
        else {
            when(projectApi.getProject(repositoryId)).thenAnswer(invocation -> {
                throw new Exception("exception");
            });
        }
    }

    public void setRepositoryPermissionsToReadOnly(VcsRepositoryUrl repositoryUrl, String projectKey, Set<de.tum.in.www1.artemis.domain.User> users) throws GitLabApiException {
        for (var user : users) {
            mockGetUserId(user.getLogin(), true);
            final var repositoryId = getPathIDFromRepositoryURL(repositoryUrl);
            doReturn(new Member()).when(projectApi).updateMember(repositoryId, 1, GUEST);
        }
    }
}
