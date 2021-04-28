package de.tum.in.www1.artemis.service.connectors.gitlab;

import static org.gitlab4j.api.models.AccessLevel.*;

import java.util.*;

import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.AccessLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
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
        final var gitlabUserId = getUserIdCreateIfNotExists(user);
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
        var userApi = gitlabApi.getUserApi();

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

        var password = shouldUpdatePassword ? passwordService.decryptPassword(user) : null;
        return userApi.updateUser(gitlabUser, password);
    }

    @Override
    public void updateCoursePermissions(Course updatedCourse, String oldInstructorGroup, String oldTeachingAssistantGroup) {
        if (oldInstructorGroup.equals(updatedCourse.getInstructorGroupName()) && oldTeachingAssistantGroup.equals(updatedCourse.getTeachingAssistantGroupName())) {
            // Do nothing if the group names didn't change
            return;
        }

        final var exercises = programmingExerciseRepository.findAllByCourse(updatedCourse);
        // All users that we already updated

        // Update the old instructors of the course
        final var oldInstructors = userRepository.findAllInGroupWithAuthorities(oldInstructorGroup);
        // doUpgrade=false, because these users already are instructors.
        updateOldGroupMembers(exercises, oldInstructors, updatedCourse.getInstructorGroupName(), updatedCourse.getTeachingAssistantGroupName(), REPORTER, false);
        final var processedUsers = new HashSet<>(oldInstructors);

        // Update the old teaching assistant of the group
        final var oldTeachingAssistants = userRepository.findAllUserInGroupAndNotIn(oldTeachingAssistantGroup, oldInstructors);
        // doUpgrade=true, because these users should be upgraded from TA to instructor, if possible.
        updateOldGroupMembers(exercises, oldTeachingAssistants, updatedCourse.getTeachingAssistantGroupName(), updatedCourse.getInstructorGroupName(), MAINTAINER, true);
        processedUsers.addAll(oldTeachingAssistants);

        // Now, we only have to add all users that have not been updated yet AND that are part of one of the new groups
        // Find all NEW instructors, that did not belong to the old TAs or instructors
        final var remainingInstructors = userRepository.findAllUserInGroupAndNotIn(updatedCourse.getInstructorGroupName(), processedUsers);
        remainingInstructors.forEach(user -> {
            final var userId = getUserId(user.getLogin());
            addUserToGroupsOfExercises(userId, exercises, MAINTAINER);
        });
        processedUsers.addAll(remainingInstructors);

        // Find all NEW TAs that did not belong to the old TAs or instructors
        final var remainingTeachingAssistants = userRepository.findAllUserInGroupAndNotIn(updatedCourse.getTeachingAssistantGroupName(), processedUsers);
        remainingTeachingAssistants.forEach(user -> {
            final var userId = getUserId(user.getLogin());
            addUserToGroupsOfExercises(userId, exercises, REPORTER);
        });
    }

    /**
     * Updates all exercise groups in GitLab for the new course instructor/TA group names. Removes users that are no longer
     * in any group and moves users to the new group, if they still have a valid group (e.g. instructor to TA).
     * If a user still belongs to a group that is valid for the same access level, he will stay on this level. If a user
     * can be upgraded, i.e. from instructor to TA, this will also be done.
     * All cases:
     * <ul>
     *     <li>DOWNGRADE from instructor to TA</li>
     *     <li>STAY instructor, because other group name is valid for newInstructorGroup</li>
     *     <li>UPGRADE from TA to instructor</li>
     *     <li>REMOVAL from GitLab group, because no valid active group is present</li>
     * </ul>
     *
     * @param exercises              All exercises for the updated course
     * @param users                  All user in the old group
     * @param newGroupName           The name of the new group, e.g. "newInstructors"
     * @param alternativeGroupName   The name of the other group (instructor or TA), e.g. "newTeachingAssistant"
     * @param alternativeAccessLevel The access level for the alternative group, e.g. REPORTER for TAs
     * @param doUpgrade              True, if the alternative group would be an upgrade. This is the case if the old group was TA, so the new instructor group would be better (if applicable)
     */
    private void updateOldGroupMembers(List<ProgrammingExercise> exercises, List<User> users, String newGroupName, String alternativeGroupName, AccessLevel alternativeAccessLevel,
            boolean doUpgrade) {
        for (final var user : users) {
            final var userId = getUserId(user.getLogin());
            /*
             * Contains the access level of the other group, to which the user currently does NOT belong, IF the user could be in that group E.g. user1(groups=[foo,bar]),
             * oldInstructorGroup=foo, oldTAGroup=bar; newInstructorGroup=instr newTAGroup=bar So, while the instructor group changed, the TA group stayed the same. user1 was part
             * of the old instructor group, but isn't any more. BUT he could be a TA according to the new groups, so the alternative access level would be the level of the TA
             * group, i.e. REPORTER
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

            exercises.forEach(exercise -> {
                try {
                    /*
                     * Update the user, if 1. The user can be upgraded: TA -> instructor 2. We have to downgrade the user (instructor -> TA), if he only belongs to the new TA
                     * group, but not to the instructor group any more
                     */
                    if (newAccessLevel.isPresent()) {
                        gitlabApi.getGroupApi().updateMember(exercise.getProjectKey(), userId, newAccessLevel.get());
                    }
                    else {
                        // Remove the user from the all groups, if he no longer is a TA, or instructor
                        gitlabApi.getGroupApi().removeMember(exercise.getProjectKey(), userId);
                    }
                }
                catch (GitLabApiException e) {
                    throw new GitLabException("Error while updating GitLab group " + exercise.getProjectKey(), e);
                }
            });
        }
    }

    @Override
    public void deleteVcsUser(String login) {
        try {
            // Delete by login String doesn't work, so we need to get the actual userId first.
            final var userId = getUserId(login);
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

        var exercises = programmingExerciseRepository.findAllByInstructorOrTAGroupNameIn(groups);
        for (var exercise : exercises) {
            var instructorGroupName = exercise.getCourseViaExerciseGroupOrCourseMember().getInstructorGroupName();
            var accessLevel = groups.contains(instructorGroupName) ? MAINTAINER : REPORTER;
            addUserToGroup(exercise.getProjectKey(), gitlabUserId, accessLevel);
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
        var exercises = programmingExerciseRepository.findAllByInstructorOrTAGroupNameIn(groupsToRemove);
        for (var exercise : exercises) {
            var course = exercise.getCourseViaExerciseGroupOrCourseMember();
            var instructorGroup = course.getInstructorGroupName();
            var teachingAssisstantGroup = course.getTeachingAssistantGroupName();

            // Do not remove the user from the group and only update it's access level
            var shouldUpdateGroupAccess = userGroups.contains(instructorGroup) || userGroups.contains(teachingAssisstantGroup);
            if (shouldUpdateGroupAccess) {
                var accessLevel = userGroups.contains(instructorGroup) ? MAINTAINER : REPORTER;
                gitlabApi.getGroupApi().updateMember(exercise.getProjectKey(), gitlabUserId, accessLevel);
            }
            else {
                removeUserFromGroup(gitlabUserId, exercise.getProjectKey());
            }
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
