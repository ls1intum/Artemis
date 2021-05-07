package de.tum.in.www1.artemis.service.connectors.gitlab;

import static org.gitlab4j.api.models.AccessLevel.*;

import java.util.*;

import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.UserApi;
import org.gitlab4j.api.models.AccessLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.connectors.VcsUserManagementService;
import de.tum.in.www1.artemis.service.user.PasswordService;

@Service
@Profile("gitlab")
public class GitLabUserManagementService implements VcsUserManagementService {

    private final Logger log = LoggerFactory.getLogger(GitLabUserManagementService.class);

    private final PasswordService passwordService;

    private final UserRepository userRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final GitLabApi gitlabApi;

    @Value("${gitlab.use-pseudonyms:#{false}}")
    private boolean usePseudonyms;

    public GitLabUserManagementService(ProgrammingExerciseRepository programmingExerciseRepository, GitLabApi gitlabApi, UserRepository userRepository,
            PasswordService passwordService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.gitlabApi = gitlabApi;
        this.userRepository = userRepository;
        this.passwordService = passwordService;
    }

    @Override
    public void createVcsUser(User user) {
        final int gitlabUserId = getUserIdCreateIfNotExists(user);
        // Add user to existing exercises
        addUserToGroups(gitlabUserId, user.getGroups());
    }

    @Override
    public void updateVcsUser(String vcsLogin, User user, Set<String> removedGroups, Set<String> addedGroups, boolean shouldUpdatePassword) {
        try {
            var gitlabUser = updateBasicUserInformation(vcsLogin, user, shouldUpdatePassword);
            if (gitlabUser == null) {
                return;
            }

            addUserToGroups(gitlabUser.getId(), addedGroups);

            // Remove the user from groups or update it's permissions if the user belongs to multiple
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
     * @param shouldUpdatePassword if the Gitlab password should be updated
     * @return the updated Gitlab user
     * @throws GitLabApiException if the user cannot be retrieved or cannot update the user
     */
    private org.gitlab4j.api.models.User updateBasicUserInformation(String userLogin, User user, boolean shouldUpdatePassword) throws GitLabApiException {
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

        String password = shouldUpdatePassword ? passwordService.decryptPassword(user) : null;
        return userApi.updateUser(gitlabUser, password);
    }

    @Override
    public void updateCoursePermissions(Course updatedCourse, String oldInstructorGroup, String oldEditorGroup, String oldTeachingAssistantGroup) {
        if (oldInstructorGroup.equals(updatedCourse.getInstructorGroupName()) && oldEditorGroup.equals(updatedCourse.getEditorGroupName())
                && oldTeachingAssistantGroup.equals(updatedCourse.getTeachingAssistantGroupName())) {
            // Do nothing if the group names didn't change
            return;
        }

        final List<ProgrammingExercise> programmingExercises = programmingExerciseRepository.findAllByCourse(updatedCourse);

        final List<User> allUsers = userRepository.findAllInGroupWithAuthorities(oldInstructorGroup);
        allUsers.addAll(userRepository.findAllInGroupWithAuthorities(oldEditorGroup));
        allUsers.addAll(userRepository.findAllInGroupWithAuthorities(oldTeachingAssistantGroup));

        final Set<User> oldUsers = new HashSet<>();
        final Set<User> newUsers = new HashSet<>();

        for (User user : allUsers) {
            Set<String> userGroups = user.getGroups();
            if (userGroups != null) {
                if (userGroups.contains(oldTeachingAssistantGroup) || userGroups.contains(oldEditorGroup) || userGroups.contains(oldInstructorGroup)) {
                    oldUsers.add(user);
                }
                else {
                    newUsers.add(user);
                }
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
            Set<String> groups = user.getGroups();

            try {
                var gitlabUser = userApi.getUser(user.getLogin());
                if (gitlabUser == null) {
                    log.warn("User {} does not exist in Gitlab and cannot be updated!", user.getLogin());
                    continue;
                }

                if (user.getGroups() != null) {
                    final int userId = getUserId(user.getLogin());

                    if (groups.contains(updatedCourse.getInstructorGroupName())) {
                        addUserToGroupsOfExercises(userId, programmingExercises, MAINTAINER);
                    }
                    else if (groups.contains(updatedCourse.getEditorGroupName())) {
                        addUserToGroupsOfExercises(userId, programmingExercises, DEVELOPER);
                    }
                    else if (groups.contains(updatedCourse.getTeachingAssistantGroupName())) {
                        addUserToGroupsOfExercises(userId, programmingExercises, REPORTER);
                    }
                    else {
                        removeMemberFromExercises(programmingExercises, gitlabUser.getId());
                    }
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
                if (user.getGroups() == null) {
                    removeMemberFromExercises(programmingExercises, gitlabUser.getId());
                }

                if (groups.contains(updatedCourse.getInstructorGroupName())) {
                    updateMemberExercisePermissions(programmingExercises, gitlabUser.getId(), MAINTAINER);
                }
                else if (groups.contains(updatedCourse.getEditorGroupName())) {
                    updateMemberExercisePermissions(programmingExercises, gitlabUser.getId(), DEVELOPER);
                }
                else if (groups.contains(updatedCourse.getTeachingAssistantGroupName())) {
                    updateMemberExercisePermissions(programmingExercises, gitlabUser.getId(), REPORTER);
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
    private void updateMemberExercisePermissions(List<ProgrammingExercise> programmingExercises, Integer gitlabUserId, AccessLevel accessLevel) {
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
    private void removeMemberFromExercises(List<ProgrammingExercise> programmingExercises, Integer gitlabUserId) {
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
            final int userId = getUserId(login);
            gitlabApi.getUserApi().deleteUser(userId, true);
        }
        catch (GitLabUserDoesNotExistException e) {
            log.warn("Cannot delete user ''{}'' in GitLab. User does not exist!", login);
        }
        catch (GitLabApiException e) {
            throw new GitLabException(String.format("Cannot delete user %s from GitLab!", login), e);
        }
    }

    /**
     * Gets the Gitlab user id of the Artemis user. Creates
     * a new Gitlab user if it doesn't exist and returns the
     * generated id.
     *
     * @param user the Artemis user
     * @return the Gitlab user id
     */
    private int getUserIdCreateIfNotExists(User user) {
        try {
            var gitlabUser = gitlabApi.getUserApi().getUser(user.getLogin());
            if (gitlabUser == null) {
                gitlabUser = createUser(user);
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
    private void addUserToGroups(int gitlabUserId, Set<String> groups) throws GitLabException {
        if (groups == null || groups.isEmpty()) {
            return;
        }

        List<ProgrammingExercise> exercises = programmingExerciseRepository.findAllByInstructorOrEditorOrTAGroupNameIn(groups);
        for (var exercise : exercises) {
            Optional<AccessLevel> accessLevel = getAccessLevelFromUserGroups(groups, exercise);
            if (accessLevel.isPresent()) {
                addUserToGroup(exercise.getProjectKey(), gitlabUserId, accessLevel.get());
            }
        }
    }

    /**
     * Adds a Gitlab user to all Gitlab groups mapped to the provided exercises.
     *
     * @param userId      the Gitlab user id
     * @param exercises   the list of exercises which project key is used as the Gitlab "group" (i.e. Gitlab project)
     * @param accessLevel the access level that the user should get as part of the group/project
     */
    public void addUserToGroupsOfExercises(int userId, List<ProgrammingExercise> exercises, AccessLevel accessLevel) throws GitLabException {
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
    private void addUserToGroup(String groupName, int gitlabUserId, AccessLevel accessLevel) throws GitLabException {
        try {
            gitlabApi.getGroupApi().addMember(groupName, gitlabUserId, accessLevel);
        }
        catch (GitLabApiException e) {
            if (e.getMessage().equals("Member already exists")) {
                log.warn("Member already exists for group {}", groupName);
                return;
            }
            throw new GitLabException(String.format("Error adding new user [%d] to group [%s]", gitlabUserId, groupName), e);
        }
    }

    /**
     *
     * @param gitlabUserId
     * @param userGroups groups that the user belongs to
     * @param groupsToRemove groups where the user should be removed from
     * @throws GitLabApiException if an error occured while updating the user
     */
    private void removeOrUpdateUserFromGroups(int gitlabUserId, Set<String> userGroups, Set<String> groupsToRemove) throws GitLabApiException {
        if (groupsToRemove == null || groupsToRemove.isEmpty()) {
            return;
        }

        // Gitlab groups are identified by the project key of the programming exercise
        var exercises = programmingExerciseRepository.findAllByInstructorOrEditorOrTAGroupNameIn(groupsToRemove);
        for (var exercise : exercises) {
            Optional<AccessLevel> accessLevel = getAccessLevelFromUserGroups(userGroups, exercise);
            // Do not remove the user from the group and only update it's access level
            var shouldUpdateGroupAccess = accessLevel.isPresent();
            if (shouldUpdateGroupAccess) {
                gitlabApi.getGroupApi().updateMember(exercise.getProjectKey(), gitlabUserId, accessLevel.get());
            }
            else {
                removeUserFromGroup(gitlabUserId, exercise.getProjectKey());
            }
        }
    }

    private Optional<AccessLevel> getAccessLevelFromUserGroups(Set<String> userGroups, Exercise exercise) {
        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        String instructorGroup = course.getInstructorGroupName();
        String editorGroup = course.getEditorGroupName();
        String teachingAssisstantGroup = course.getTeachingAssistantGroupName();

        if (userGroups.contains(instructorGroup)) {
            return Optional.of(MAINTAINER);
        }
        else if (userGroups.contains(editorGroup)) {
            return Optional.of(DEVELOPER);
        }
        else if (userGroups.contains(teachingAssisstantGroup)) {
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
    private void removeUserFromGroup(int gitlabUserId, String group) {
        try {
            gitlabApi.getGroupApi().removeMember(group, gitlabUserId);
        }
        catch (GitLabApiException ex) {
            // If user membership to group is missing on Gitlab, ignore the exception.
            if (ex.getHttpStatus() != 404) {
                log.error("Gitlab Exception when removing a user " + gitlabUserId + " to a group " + group, ex);
            }
        }
    }

    /**
     * Creates a Gitlab user account based on the passed Artemis
     * user account with the same email, login, name and password
     *
     * @param user The artemis user
     * @return a Gitlab user
     */
    public org.gitlab4j.api.models.User createUser(User user) {
        try {
            final var gitlabUser = new org.gitlab4j.api.models.User().withEmail(user.getEmail()).withUsername(user.getLogin()).withName(getUsersName(user))
                    .withCanCreateGroup(false).withCanCreateProject(false).withSkipConfirmation(true);
            return gitlabApi.getUserApi().createUser(gitlabUser, passwordService.decryptPassword(user), false);
        }
        catch (GitLabApiException e) {
            throw new GitLabException("Unable to create new user in GitLab " + user.getLogin(), e);
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
     * Retrieves the user id of the Gitlab user with the given user name
     *
     * @param username the username for which the user id should be retrieved
     * @return the Gitlab user id
     */
    public int getUserId(String username) {
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
}
