package de.tum.in.www1.artemis.service.connectors.gitlab;

import static org.gitlab4j.api.models.AccessLevel.*;

import java.util.*;

import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.UserApi;
import org.gitlab4j.api.models.AccessLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.exception.VersionControlException;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.connectors.VcsUserManagementService;
import de.tum.in.www1.artemis.service.connectors.gitlab.dto.GitLabPersonalAccessTokenRequestDTO;
import de.tum.in.www1.artemis.service.connectors.gitlab.dto.GitLabPersonalAccessTokenResponseDTO;

@Service
@Profile("gitlab")
public class GitLabUserManagementService implements VcsUserManagementService {

    private final Logger log = LoggerFactory.getLogger(GitLabUserManagementService.class);

    private final UserRepository userRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final GitLabApi gitlabApi;

    protected final RestTemplate restTemplate;

    @Value("${gitlab.use-pseudonyms:#{false}}")
    private boolean usePseudonyms;

    @Value("${artemis.version-control.version-control-access-token:#{false}}")
    private Boolean versionControlAccessToken;

    public GitLabUserManagementService(ProgrammingExerciseRepository programmingExerciseRepository, GitLabApi gitlabApi, UserRepository userRepository,
            @Qualifier("gitlabRestTemplate") RestTemplate restTemplate) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.gitlabApi = gitlabApi;
        this.userRepository = userRepository;
        this.restTemplate = restTemplate;
    }

    @Override
    public void createVcsUser(User user, String password) throws VersionControlException {
        final Long gitlabUserId = getUserIdCreateIfNotExists(user, password);
        // Add user to existing exercises
        addUserToGroups(gitlabUserId, user.getGroups());
    }

    @Override
    public void updateVcsUser(String vcsLogin, User user, Set<String> removedGroups, Set<String> addedGroups, String newPassword) {
        try {
            var gitlabUser = updateBasicUserInformation(vcsLogin, user, newPassword);
            if (gitlabUser == null) {
                return;
            }

            updateUserActivationState(user, gitlabUser.getId());

            addUserToGroups(gitlabUser.getId(), addedGroups);

            // Remove the user from groups or update its permissions if the user belongs to multiple
            // groups of the same course.
            removeOrUpdateUserFromGroups(gitlabUser.getId(), user.getGroups(), removedGroups);
        }
        catch (GitLabApiException e) {
            throw new GitLabException("Error while trying to update user in GitLab: " + user, e);
        }
    }

    /**
     * Updates the basic information of the Gitlab user based on the passed Artemis user.
     *
     * @param userLogin the username of the user
     * @param user the Artemis user to update
     * @param newPassword if provided the Gitlab password should be updated to the new value
     * @return the updated Gitlab user
     * @throws GitLabApiException if the user cannot be retrieved or cannot update the user
     */
    private org.gitlab4j.api.models.User updateBasicUserInformation(String userLogin, User user, String newPassword) throws GitLabApiException {
        UserApi userApi = gitlabApi.getUserApi();

        final var gitlabUser = userApi.getUser(userLogin);
        if (gitlabUser == null) {
            // in case the user does not exist in Gitlab, we cannot update it
            log.warn("User {} does not exist in Gitlab and cannot be updated!", userLogin);
            return null;
        }

        gitlabUser.setName(getUsersName(user));
        gitlabUser.setUsername(user.getLogin());
        gitlabUser.setEmail(user.getEmail());
        // Skip confirmation is necessary in order to update the email without user re-confirmation
        gitlabUser.setSkipConfirmation(true);

        return userApi.updateUser(gitlabUser, newPassword);
    }

    /**
     * Updates the activation state of the Gitlab account based on the Artemis account.
     * We
     * @param user The Artemis user
     * @param gitlabUserId the id of the GitLab user that is mapped to the Artemis user
     */
    private void updateUserActivationState(User user, Long gitlabUserId) throws GitLabApiException {
        if (user.getActivated()) {
            gitlabApi.getUserApi().unblockUser(gitlabUserId);
        }
        else {
            gitlabApi.getUserApi().blockUser(gitlabUserId);
        }
    }

    @Override
    public void updateCoursePermissions(Course updatedCourse, String oldInstructorGroup, String oldEditorGroup, String oldTeachingAssistantGroup) {
        if (oldInstructorGroup.equals(updatedCourse.getInstructorGroupName()) && oldEditorGroup.equals(updatedCourse.getEditorGroupName())
                && oldTeachingAssistantGroup.equals(updatedCourse.getTeachingAssistantGroupName())) {
            // Do nothing if the group names didn't change
            return;
        }

        final List<ProgrammingExercise> programmingExercises = programmingExerciseRepository.findAllProgrammingExercisesInCourseOrInExamsOfCourse(updatedCourse);
        log.info("Update Gitlab permissions for programming exercises: {}", programmingExercises.stream().map(ProgrammingExercise::getProjectKey).toList());
        // TODO: in case we update a tutor group / role here, the tutor should NOT get access to exam exercises before the exam has finished

        final List<User> allUsers = userRepository.findAllInGroupWithAuthorities(oldInstructorGroup);
        allUsers.addAll(userRepository.findAllInGroupWithAuthorities(oldEditorGroup));
        allUsers.addAll(userRepository.findAllInGroupWithAuthorities(oldTeachingAssistantGroup));
        allUsers.addAll(userRepository.findAllUserInGroupAndNotIn(updatedCourse.getInstructorGroupName(), allUsers));
        allUsers.addAll(userRepository.findAllUserInGroupAndNotIn(updatedCourse.getEditorGroupName(), allUsers));
        allUsers.addAll(userRepository.findAllUserInGroupAndNotIn(updatedCourse.getTeachingAssistantGroupName(), allUsers));

        final Set<User> oldUsers = new HashSet<>();
        final Set<User> newUsers = new HashSet<>();

        for (User user : allUsers) {
            Set<String> userGroups = user.getGroups();
            if (userGroups.contains(oldTeachingAssistantGroup) || userGroups.contains(oldEditorGroup) || userGroups.contains(oldInstructorGroup)) {
                oldUsers.add(user);
            }
            else {
                newUsers.add(user);
            }
        }

        updateOldGroupMembers(programmingExercises, oldUsers, updatedCourse);
        setPermissionsForNewGroupMembers(programmingExercises, newUsers, updatedCourse);
    }

    /**
     * Sets the permission for users that have not been in a group before.
     * The permissions are updated for all programming exercises of a course, according to the user groups the user is part of.
     *
     * @param programmingExercises  all programming exercises of the passed updatedCourse
     * @param newUsers              users of the passed course that have not been in a group before
     * @param updatedCourse         course with updated groups
     */
    private void setPermissionsForNewGroupMembers(List<ProgrammingExercise> programmingExercises, Set<User> newUsers, Course updatedCourse) {
        final var userApi = gitlabApi.getUserApi();

        for (User user : newUsers) {
            try {
                var gitlabUser = userApi.getUser(user.getLogin());
                if (gitlabUser == null) {
                    log.warn("User {} does not exist in Gitlab and cannot be updated!", user.getLogin());
                    continue;
                }

                Optional<AccessLevel> accessLevel = getAccessLevelFromUserGroups(user.getGroups(), updatedCourse);
                if (accessLevel.isPresent()) {
                    addUserToGroupsOfExercises(gitlabUser.getId(), programmingExercises, accessLevel.get());
                }
                else {
                    removeMemberFromExercises(programmingExercises, gitlabUser.getId());
                }
            }
            catch (GitLabApiException e) {
                throw new GitLabException("Error while trying to set permission for user in GitLab: " + user, e);
            }
        }
    }

    /**
     * Updates the permission for users that have been in a group before.
     * The permissions are updated for all programming exercises of a course, according to the user groups the user is part of.
     *
     * @param programmingExercises  programming exercises of the passed updatedCourse
     * @param oldUsers              users of the passed course that have been in a group before
     * @param updatedCourse         course with updated groups
     */
    private void updateOldGroupMembers(List<ProgrammingExercise> programmingExercises, Set<User> oldUsers, Course updatedCourse) {
        final var userApi = gitlabApi.getUserApi();

        for (User user : oldUsers) {
            try {
                var gitlabUser = userApi.getUser(user.getLogin());
                if (gitlabUser == null) {
                    log.warn("User {} does not exist in Gitlab and cannot be updated!", user.getLogin());
                    continue;
                }

                Set<String> groups = user.getGroups();
                if (groups == null) {
                    removeMemberFromExercises(programmingExercises, gitlabUser.getId());
                    continue;
                }

                Optional<AccessLevel> accessLevel = getAccessLevelFromUserGroups(groups, updatedCourse);
                if (accessLevel.isPresent()) {
                    updateMemberExercisePermissions(programmingExercises, gitlabUser.getId(), accessLevel.get());
                }
                else {
                    removeMemberFromExercises(programmingExercises, gitlabUser.getId());
                }
            }
            catch (GitLabApiException e) {
                throw new GitLabException("Error while trying to update user in GitLab: " + user, e);
            }
        }
    }

    /**
     * Updates the permissions for the passed user to the passed accessLevel
     *
     * @param programmingExercises  all exercises for which the permissions shall be updated
     * @param gitlabUserId          gitlabUserId for which the permissions shall be updated
     * @param accessLevel           access level that shall be set for a user
     */
    private void updateMemberExercisePermissions(List<ProgrammingExercise> programmingExercises, Long gitlabUserId, AccessLevel accessLevel) {
        programmingExercises.forEach(exercise -> {
            try {
                gitlabApi.getGroupApi().updateMember(exercise.getProjectKey(), gitlabUserId, accessLevel);
            }
            catch (GitLabApiException e) {
                throw new GitLabException("Error while updating GitLab group " + exercise.getProjectKey(), e);
            }
        });

    }

    /**
     * Removes a member from an exercise, e.g. when a group is removed and the user is not part of another group that has permissions.
     *
     * @param programmingExercises  all exercises for which the permissions shall be updated
     * @param gitlabUserId          gitlabUserId for which the permissions shall be updated
     */
    private void removeMemberFromExercises(List<ProgrammingExercise> programmingExercises, Long gitlabUserId) {
        programmingExercises.forEach(exercise -> {
            try {
                gitlabApi.getGroupApi().removeMember(exercise.getProjectKey(), gitlabUserId);
            }
            catch (GitLabApiException e) {
                throw new GitLabException("Error while updating GitLab group " + exercise.getProjectKey(), e);
            }
        });

    }

    @Override
    public void deleteVcsUser(String login) {
        try {
            // Delete by login String doesn't work, so we need to get the actual userId first.
            final Long userId = getUserId(login);
            gitlabApi.getUserApi().deleteUser(userId, true);
        }
        catch (GitLabUserDoesNotExistException e) {
            log.warn("Cannot delete user ''{}'' in GitLab. User does not exist!", login);
        }
        catch (GitLabApiException e) {
            throw new GitLabException(String.format("Cannot delete user %s from GitLab!", login), e);
        }
    }

    @Override
    public void deactivateUser(String login) throws VersionControlException {
        try {
            final Long userId = getUserId(login);
            // We block the user instead of deactivating because a deactivated account
            // is activated automatically when the user logs into Gitlab.
            gitlabApi.getUserApi().blockUser(userId);
        }
        catch (GitLabApiException e) {
            throw new GitLabException(String.format("Cannot block user %s from GitLab!", login), e);
        }
    }

    @Override
    public void activateUser(String login) throws VersionControlException {
        try {
            final Long userId = getUserId(login);
            gitlabApi.getUserApi().unblockUser(userId);
        }
        catch (GitLabApiException e) {
            throw new GitLabException(String.format("Cannot unblock user %s from GitLab!", login), e);
        }
    }

    /**
     * Gets the Gitlab user id of the Artemis user. Creates
     * a new Gitlab user if it doesn't exist and returns the
     * generated id.
     *
     * @param user the Artemis user
     * @param password the user's password
     * @return the Gitlab user id
     */
    private Long getUserIdCreateIfNotExists(User user, String password) {
        try {
            var gitlabUser = gitlabApi.getUserApi().getUser(user.getLogin());
            if (gitlabUser == null) {
                gitlabUser = createUser(user, password);
            }
            return gitlabUser.getId();
        }
        catch (GitLabApiException e) {
            throw new GitLabException("Unable to get ID for user " + user.getLogin(), e);
        }
    }

    /**
     * Adds the Gitlab user to the groups. It will be given a different access level
     * based on the group type (instructors are given the MAINTAINER level and teaching
     * assistants REPORTED).
     *
     * @param gitlabUserId the user id of the Gitlab user
     * @param groups the new groups
     */
    private void addUserToGroups(Long gitlabUserId, Set<String> groups) {
        if (groups == null || groups.isEmpty()) {
            return;
        }

        List<ProgrammingExercise> exercises = programmingExerciseRepository.findAllByInstructorOrEditorOrTAGroupNameIn(groups);
        log.info("Update Gitlab permissions for programming exercises: {}", exercises.stream().map(ProgrammingExercise::getProjectKey).toList());
        // TODO: in case we update a tutor group / role here, the tutor should NOT get access to exam exercises before the exam has finished
        for (var exercise : exercises) {
            Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
            Optional<AccessLevel> accessLevel = getAccessLevelFromUserGroups(groups, course);
            accessLevel.ifPresent(level -> addUserToGroup(exercise.getProjectKey(), gitlabUserId, level));
        }
    }

    /**
     * Adds a Gitlab user to all Gitlab groups mapped to the provided exercises.
     *
     * @param userId      the Gitlab user id
     * @param exercises   the list of exercises which project key is used as the Gitlab "group" (i.e. Gitlab project)
     * @param accessLevel the access level that the user should get as part of the group/project
     */
    public void addUserToGroupsOfExercises(Long userId, List<ProgrammingExercise> exercises, AccessLevel accessLevel) throws GitLabException {
        for (final var exercise : exercises) {
            addUserToGroup(exercise.getProjectKey(), userId, accessLevel);
        }
    }

    /**
     * Adds a Gitlab user to a Gitlab group with the given access level.
     *
     * @param groupName The name of the Gitlab group
     * @param gitlabUserId the id of the Gitlab user
     * @param accessLevel the access level to grant to the user
     * @throws GitLabException if the user cannot be added to the group
     */
    private void addUserToGroup(String groupName, Long gitlabUserId, AccessLevel accessLevel) throws GitLabException {
        try {
            log.info("Add member {} to Gitlab group {}", gitlabUserId, groupName);
            gitlabApi.getGroupApi().addMember(groupName, gitlabUserId, accessLevel);
        }
        catch (GitLabApiException e) {
            if (e.getMessage().equals("Member already exists")) {
                log.warn("Member already exists for group {}", groupName);
                return;
            }
            else if (e.getHttpStatus() == 404) {
                log.warn("Group not found {}", groupName);
                return;
            }
            throw new GitLabException(String.format("Error adding new user [%d] to group [%s]", gitlabUserId, groupName), e);
        }
    }

    /**
     * Removes or updates the user to or from the groups.
     *
     * @param gitlabUserId the Gitlab user id
     * @param userGroups groups that the user belongs to
     * @param groupsToRemove groups where the user should be removed from
     */
    private void removeOrUpdateUserFromGroups(Long gitlabUserId, Set<String> userGroups, Set<String> groupsToRemove) throws GitLabApiException {
        if (groupsToRemove == null || groupsToRemove.isEmpty()) {
            return;
        }

        // Gitlab groups are identified by the project key of the programming exercise
        var exercises = programmingExerciseRepository.findAllByInstructorOrEditorOrTAGroupNameIn(groupsToRemove);
        log.info("Update Gitlab permissions for programming exercises: {}", exercises.stream().map(ProgrammingExercise::getProjectKey).toList());
        for (var exercise : exercises) {
            // TODO: in case we update a tutor group / role here, the tutor should NOT get access to exam exercises before the exam has finished
            Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
            Optional<AccessLevel> accessLevel = getAccessLevelFromUserGroups(userGroups, course);
            // Do not remove the user from the group and only update its access level
            var shouldUpdateGroupAccess = accessLevel.isPresent();
            if (shouldUpdateGroupAccess) {
                gitlabApi.getGroupApi().updateMember(exercise.getProjectKey(), gitlabUserId, accessLevel.get());
            }
            else {
                removeUserFromGroup(gitlabUserId, exercise.getProjectKey());
            }
        }
    }

    /**
     * Returns the Gitlab access level of the user for a given course. The access level is computed
     * by checking if the user belongs to any of the course's group.
     *
     * @param userGroups The groups that the user belongs to
     * @param course the course to get the access level from
     * @return the access level
     */
    public Optional<AccessLevel> getAccessLevelFromUserGroups(Set<String> userGroups, Course course) {
        String instructorGroup = course.getInstructorGroupName();
        String editorGroup = course.getEditorGroupName();
        String teachingAssistantGroup = course.getTeachingAssistantGroupName();

        if (userGroups.contains(instructorGroup)) {
            return Optional.of(OWNER);
        }
        else if (userGroups.contains(editorGroup)) {
            return Optional.of(MAINTAINER);
        }
        else if (userGroups.contains(teachingAssistantGroup)) {
            return Optional.of(REPORTER);
        }
        else {
            return Optional.empty();
        }
    }

    /**
     * Removes the Gitlab user from the specified group. Doesn't throw
     * an error if the user isn't member of the group.
     *
     * @param gitlabUserId the user id of the Gitlab user
     * @param group the group to remove the user from
     */
    private void removeUserFromGroup(Long gitlabUserId, String group) throws GitLabApiException {
        try {
            gitlabApi.getGroupApi().removeMember(group, gitlabUserId);
        }
        catch (GitLabApiException ex) {
            // If user membership to group is missing on Gitlab, ignore the exception.
            if (ex.getHttpStatus() != 404) {
                log.error("Gitlab Exception when removing a user {} to a group {}", gitlabUserId, group, ex);
                throw ex;
            }
        }
    }

    /**
     * Creates a Gitlab user account based on the passed Artemis
     * user account with the same email, login, name and password
     *
     * @param user The artemis user
     * @param password The user's password
     * @return a Gitlab user
     */
    public org.gitlab4j.api.models.User createUser(User user, String password) {
        try {
            var gitlabUser = new org.gitlab4j.api.models.User().withEmail(user.getEmail()).withUsername(user.getLogin()).withName(getUsersName(user)).withCanCreateGroup(false)
                    .withCanCreateProject(false).withSkipConfirmation(true);
            gitlabUser = gitlabApi.getUserApi().createUser(gitlabUser, password, false);
            generateVersionControlAccessTokenIfNecessary(gitlabUser, user);
            return gitlabUser;
        }
        catch (GitLabApiException e) {
            throw new GitLabException("Unable to create new user in GitLab " + user.getLogin(), e);
        }
    }

    /**
     * Generate a version control access token and store it in the user object, if it is needed.
     * It is needed if
     * 1. the config option is enabled, and
     * 2. the user does not yet have an access token
     *
     * The GitLab user will be extracted from the Gitlab user API
     *
     * @param user the Artemis user (where the token will be stored)
     */
    public void generateVersionControlAccessTokenIfNecessary(User user) {
        UserApi userApi = gitlabApi.getUserApi();

        final org.gitlab4j.api.models.User gitlabUser;
        try {
            gitlabUser = userApi.getUser(user.getLogin());
            if (gitlabUser == null) {
                // No GitLab user is found -> Do nothing
                return;
            }

            generateVersionControlAccessTokenIfNecessary(gitlabUser, user);
        }
        catch (GitLabApiException e) {
            log.error("Could not generate a Gitlab access token for user {}", user.getLogin(), e);
        }
    }

    /**
     * Generate a version control access token and store it in the user object, if it is needed.
     * It is needed if
     * 1. the config option is enabled, and
     * 2. the user does not yet have an access token
     * @param gitlabUser the Gitlab user (for which the token will be created)
     * @param user the Artemis user (where the token will be stored)
     */
    private void generateVersionControlAccessTokenIfNecessary(org.gitlab4j.api.models.User gitlabUser, User user) {
        if (versionControlAccessToken && user.getVcsAccessToken() == null) {
            String personalAccessToken = createPersonalAccessToken(gitlabUser.getId());
            user.setVcsAccessToken(personalAccessToken);
            userRepository.save(user);
        }
    }

    /**
     * Gets the name of the user or its pseudonym if the option
     * is enabled.
     *
     * @param user the Artemis user
     * @return the name or pseudonym
     */
    private String getUsersName(User user) {
        // Get User's name by checking the use of pseudonyms
        String name;
        if (usePseudonyms) {
            name = String.format("User %s", user.getLogin());
        }
        else {
            name = user.getName();
        }
        return name;
    }

    /**
     * Retrieves the user id of the Gitlab user with the given username
     *
     * @param username the username for which the user id should be retrieved
     * @return the Gitlab user id
     */
    public Long getUserId(String username) {
        try {
            var gitlabUser = gitlabApi.getUserApi().getUser(username);
            if (gitlabUser != null) {
                return gitlabUser.getId();
            }
            throw new GitLabUserDoesNotExistException(username);
        }
        catch (GitLabApiException e) {
            throw new GitLabException("Unable to get ID for user " + username, e);
        }
    }

    /**
     * Create a personal access token for the user with the given id.
     * The token has scopes "read_repository" and "write_repository".
     *
     * @param userId the id of the user in Gitlab
     * @return the personal access token created for that user
     */
    private String createPersonalAccessToken(Long userId) {
        // TODO: Change this to Gitlab4J api once it's supported: https://github.com/gitlab4j/gitlab4j-api/issues/653
        var body = new GitLabPersonalAccessTokenRequestDTO("Artemis-Automatic-Access-Token", userId, new String[] { "read_repository", "write_repository" });

        var entity = new HttpEntity<>(body);

        try {
            var response = restTemplate.exchange(gitlabApi.getGitLabServerUrl() + "/api/v4/users/" + userId + "/personal_access_tokens", HttpMethod.POST, entity,
                    GitLabPersonalAccessTokenResponseDTO.class);
            GitLabPersonalAccessTokenResponseDTO responseBody = response.getBody();
            if (responseBody == null || responseBody.getToken() == null) {
                log.error("Could not create Gitlab personal access token for user with id {}, response is null", userId);
                throw new GitLabException("Error while creating personal access token");
            }
            return responseBody.getToken();
        }
        catch (HttpClientErrorException e) {
            log.error("Could not create Gitlab personal access token for user with id {}, response is null", userId);
            throw new GitLabException("Error while creating personal access token");
        }
    }
}
