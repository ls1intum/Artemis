package de.tum.in.www1.artemis.service.connectors.gitlab;

import static org.gitlab4j.api.models.AccessLevel.*;

import java.util.*;
import java.util.stream.Collectors;

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
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.exception.VersionControlException;
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
    public void createVcsUser(User user) throws VersionControlException {
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

            updateUserActivationState(user, gitlabUser.getId());

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

    /**
     * Updates the activation state of the Gitlab account based on the Artemis account.
     * We
     * @param user The Artemis user
     * @param gitlabUserId the id of the GitLab user that is mapped to the Artemis user
     */
    private void updateUserActivationState(User user, int gitlabUserId) throws GitLabApiException {
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
        log.info("Update Gitlab permissions for programming exercises: " + programmingExercises.stream().map(ProgrammingExercise::getProjectKey).collect(Collectors.toList()));
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

    @Override
    public void deactivateUser(String login) throws VersionControlException {
        try {
            final int userId = getUserId(login);
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
            final int userId = getUserId(login);
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
    private void addUserToGroups(int gitlabUserId, Set<String> groups) {
        if (groups == null || groups.isEmpty()) {
            return;
        }

        List<ProgrammingExercise> exercises = programmingExerciseRepository.findAllByInstructorOrEditorOrTAGroupNameIn(groups);
        log.info("Update Gitlab permissions for programming exercises: " + exercises.stream().map(ProgrammingExercise::getProjectKey).collect(Collectors.toList()));
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
            log.info("Add member " + gitlabUserId + " to Gitlab group " + groupName);
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
    private void removeOrUpdateUserFromGroups(int gitlabUserId, Set<String> userGroups, Set<String> groupsToRemove) throws GitLabApiException {
        if (groupsToRemove == null || groupsToRemove.isEmpty()) {
            return;
        }

        // Gitlab groups are identified by the project key of the programming exercise
        var exercises = programmingExerciseRepository.findAllByInstructorOrEditorOrTAGroupNameIn(groupsToRemove);
        log.info("Update Gitlab permissions for programming exercises: " + exercises.stream().map(ProgrammingExercise::getProjectKey).collect(Collectors.toList()));
        for (var exercise : exercises) {
            // TODO: in case we update a tutor group / role here, the tutor should NOT get access to exam exercises before the exam has finished
            Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
            Optional<AccessLevel> accessLevel = getAccessLevelFromUserGroups(userGroups, course);
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
    private void removeUserFromGroup(int gitlabUserId, String group) throws GitLabApiException {
        try {
            gitlabApi.getGroupApi().removeMember(group, gitlabUserId);
        }
        catch (GitLabApiException ex) {
            // If user membership to group is missing on Gitlab, ignore the exception.
            if (ex.getHttpStatus() != 404) {
                log.error("Gitlab Exception when removing a user " + gitlabUserId + " to a group " + group, ex);
                throw ex;
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
