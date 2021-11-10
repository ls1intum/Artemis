package de.tum.in.www1.artemis.usermanagement.connector;

import static org.gitlab4j.api.models.AccessLevel.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.stream.Collectors;

import org.gitlab4j.api.*;
import org.gitlab4j.api.models.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.connectors.gitlab.GitLabUserDoesNotExistException;
import de.tum.in.www1.artemis.service.user.PasswordService;

@Component
@Profile("gitlab")
public class GitlabRequestMockProvider {

    private final RestTemplate restTemplate;

    private MockRestServiceServer mockServer;

    @SpyBean
    @InjectMocks
    private GitLabApi gitLabApi;

    @Mock
    private GroupApi groupApi;

    @Mock
    private UserApi userApi;

    @SpyBean
    private PasswordService passwordService;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    public GitlabRequestMockProvider(@Qualifier("gitlabRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void enableMockingOfRequests() {
        mockServer = MockRestServiceServer.createServer(restTemplate);
        MockitoAnnotations.openMocks(this);
    }

    public void reset() {
        mockServer.reset();
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
        doReturn(new User()).when(userApi).updateUser(any(), any());
    }

    public void mockFailOnGetUserById(String login) throws GitLabApiException {
        UserApi userApi = mock(UserApi.class);
        doReturn(userApi).when(gitLabApi).getUserApi();
        doReturn(null).when(userApi).getUser(eq(login));
    }

    /**
     * Mocks that given user is not found in GitLab and is hence created.
     *
     * @param login Login of the user whose creation is mocked
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

    private void mockImportUser(de.tum.in.www1.artemis.domain.User user, boolean shouldFail) throws GitLabApiException {
        final var gitlabUser = new User().withEmail(user.getEmail()).withUsername(user.getLogin()).withName(user.getName()).withCanCreateGroup(false)
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

    public void mockUpdateVcsUser(String login, de.tum.in.www1.artemis.domain.User user, Set<String> removedGroups, Set<String> addedGroups, boolean shouldSynchronizePassword)
            throws GitLabApiException {
        mockUpdateBasicUserInformation(login, user, shouldSynchronizePassword);
        mockUpdateUserActivationState(user, false);

        // Add as member to new groups
        if (addedGroups != null && !addedGroups.isEmpty()) {
            final var exercisesWithAddedGroups = programmingExerciseRepository.findAllByInstructorOrEditorOrTAGroupNameIn(addedGroups);
            for (final var exercise : exercisesWithAddedGroups) {
                final var accessLevel = addedGroups.contains(exercise.getCourseViaExerciseGroupOrCourseMember().getInstructorGroupName()) ? MAINTAINER : GUEST;
                doReturn(new Member()).when(groupApi).addMember(eq(exercise.getProjectKey()), anyInt(), eq(accessLevel));
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
                    doReturn(new Member()).when(groupApi).updateMember(eq(exercise.getProjectKey()), anyInt(), eq(MAINTAINER));
                }
                else if (user.getGroups().contains(course.getTeachingAssistantGroupName())) {
                    doReturn(new Member()).when(groupApi).updateMember(eq(exercise.getProjectKey()), anyInt(), eq(GUEST));
                }
                else {
                    // If the user is not a member of any relevant group anymore, we can remove him from the exercise
                    doNothing().when(groupApi).removeMember(eq(exercise.getProjectKey()), anyInt());
                }
            }
        }
    }

    public void mockUpdateVcsUserFailToActivate(String login, de.tum.in.www1.artemis.domain.User user) throws GitLabApiException {
        mockUpdateBasicUserInformation(login, user, true);
        mockUpdateUserActivationState(user, true);
    }

    public void mockUpdateBasicUserInformation(String login, de.tum.in.www1.artemis.domain.User user, boolean shouldUpdatePassword) throws GitLabApiException {
        var gitlabUser = new User().withUsername(login).withId(1);
        doReturn(gitlabUser).when(userApi).getUser(login);
        if (shouldUpdatePassword) {
            doReturn(gitlabUser).when(userApi).updateUser(gitlabUser, user.getPassword());
        }
        else {
            doReturn(gitlabUser).when(userApi).updateUser(gitlabUser, null);
        }
    }

    public void mockDeleteVcsUser(String login, boolean shouldFailToDelete) throws GitLabApiException {
        mockGetUserId(login, true, false);
        if (shouldFailToDelete) {
            doThrow(GitLabApiException.class).when(userApi).deleteUser(anyInt(), eq(true));
        }
        else {
            doNothing().when(userApi).deleteUser(anyInt(), eq(true));
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
            final var editorExercises = programmingExerciseRepository.findAllByCourse_EditorGroupNameIn(user.getGroups()).stream()
                    .filter(programmingExercise -> !instructorExercises.contains(programmingExercise)).collect(Collectors.toList());
            final var teachingAssistantExercises = programmingExerciseRepository.findAllByCourse_TeachingAssistantGroupNameIn(user.getGroups()).stream()
                    .filter(programmingExercise -> !instructorExercises.contains(programmingExercise)).collect(Collectors.toList());
            mockAddUserToGroups(userId, instructorExercises, MAINTAINER);
            mockAddUserToGroups(userId, editorExercises, DEVELOPER);
            mockAddUserToGroups(userId, teachingAssistantExercises, GUEST);
        }
    }

    public void mockAddUserToGroupsUserExists(de.tum.in.www1.artemis.domain.User user, String projectKey) throws GitLabApiException {
        int userId = mockGetUserIdCreateIfNotExist(user, false, false);
        doThrow(new GitLabApiException("Member already exists")).when(groupApi).addMember(eq(projectKey), eq(userId), any(AccessLevel.class));
    }

    public void mockAddUserToGroupsFails(de.tum.in.www1.artemis.domain.User user, String projectKey) throws GitLabApiException {
        int userId = mockGetUserIdCreateIfNotExist(user, false, false);
        doThrow(new GitLabApiException("Oh no")).when(groupApi).addMember(eq(projectKey), eq(userId), any(AccessLevel.class));
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

    public void mockDeactivateUser(String userLogin, boolean shouldFail) throws GitLabApiException {
        mockGetUserId(userLogin, true, false);
        mockBlockUser(shouldFail);
    }

    public void mockActivateUser(String userLogin, boolean shouldFail) throws GitLabApiException {
        mockGetUserId(userLogin, true, false);
        mockUnblockUser(shouldFail);
    }

    public UserApi mockUpdateUserActivationState(de.tum.in.www1.artemis.domain.User user, boolean shouldFail) throws GitLabApiException {
        if (user.getActivated()) {
            mockUnblockUser(shouldFail);
        }
        else {
            mockBlockUser(shouldFail);
        }
        return userApi;
    }

    public void mockBlockUser(boolean shouldFail) throws GitLabApiException {
        if (shouldFail) {
            doThrow(new GitLabApiException("Internal Error", 500)).when(userApi).blockUser(anyInt());
        }
        else {
            doNothing().when(userApi).blockUser(anyInt());
        }
    }

    public void mockUnblockUser(boolean shouldFail) throws GitLabApiException {
        if (shouldFail) {
            doThrow(new GitLabApiException("Internal Error", 500)).when(userApi).unblockUser(anyInt());
        }
        else {
            doNothing().when(userApi).blockUser(anyInt());
        }
    }

    public UserApi getMockedUserApi() {
        return userApi;
    }
}
