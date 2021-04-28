package de.tum.in.www1.artemis.service.connectors.gitlab;

import static org.gitlab4j.api.models.AccessLevel.*;

import java.util.*;
import java.util.stream.Collectors;

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
        final var userId = getUserIdCreateIfNotExists(user);

        // Add user to existing exercises
        if (user.getGroups() != null && user.getGroups().size() > 0) {
            final var instructorExercises = programmingExerciseRepository.findAllByCourse_InstructorGroupNameIn(user.getGroups());
            final var editorExercises = programmingExerciseRepository.findAllByCourse_EditorGroupNameIn(user.getGroups()).stream()
                    .filter(programmingExercise -> !instructorExercises.contains(programmingExercise)).collect(Collectors.toList());
            final var teachingAssistantExercises = programmingExerciseRepository.findAllByCourse_TeachingAssistantGroupNameIn(user.getGroups()).stream()
                    .filter(programmingExercise -> !instructorExercises.contains(programmingExercise)).collect(Collectors.toList());
            addUserToGroups(userId, instructorExercises, MAINTAINER);
            addUserToGroups(userId, editorExercises, DEVELOPER);
            addUserToGroups(userId, teachingAssistantExercises, REPORTER);
        }
    }

    @Override
    public void updateVcsUser(String vcsLogin, User user, Set<String> removedGroups, Set<String> addedGroups, boolean shouldSynchronizePassword) {
        try {
            var userApi = gitlabApi.getUserApi();
            final var gitlabUser = userApi.getUser(vcsLogin);
            if (gitlabUser == null) {
                // in case the user does not exist in Gitlab, we cannot update it
                log.warn("User {} does not exist in Gitlab and cannot be updated!", user.getLogin());
                return;
            }

            // Update general user information. Skip confirmation is necessary
            // in order to update the email without user re-confirmation
            gitlabUser.setName(getUsersName(user));
            gitlabUser.setUsername(user.getLogin());
            gitlabUser.setEmail(user.getEmail());
            gitlabUser.setSkipConfirmation(true);

            if (shouldSynchronizePassword) {
                // update the user password in Gitlab with the one stored in the Artemis database
                userApi.updateUser(gitlabUser, passwordService.decryptPassword(user));
            }
            else {
                userApi.updateUser(gitlabUser, null);
            }

            // Add as member to new groups
            if (addedGroups != null && !addedGroups.isEmpty()) {
                final var exercisesWithAddedGroups = programmingExerciseRepository.findAllByInstructorOrEditorOrTAGroupNameIn(addedGroups);
                for (final var exercise : exercisesWithAddedGroups) {

                    final AccessLevel accessLevel;
                    if (addedGroups.contains(exercise.getCourseViaExerciseGroupOrCourseMember().getInstructorGroupName())) {
                        accessLevel = MAINTAINER;
                    } else if (addedGroups.contains(exercise.getCourseViaExerciseGroupOrCourseMember().getEditorGroupName())) {
                        accessLevel = DEVELOPER;
                    } else {
                        accessLevel = REPORTER;
                    }

                    try {
                        gitlabApi.getGroupApi().addMember(exercise.getProjectKey(), gitlabUser.getId(), accessLevel);
                    }
                    catch (GitLabApiException ex) {
                        // if user is already member of group in GitLab, ignore the exception to synchronize the "membership" with artemis
                        // ignore other errors
                        if (!"Member already exists".equalsIgnoreCase(ex.getMessage())) {
                            log.error("Gitlab Exception when adding a user " + gitlabUser.getId() + " to a group " + exercise.getProjectKey(), ex);
                        }
                    }
                }
            }

            // Update/remove old groups
            if (removedGroups != null && !removedGroups.isEmpty()) {
                final var exercisesWithOutdatedGroups = programmingExerciseRepository.findAllByInstructorOrEditorOrTAGroupNameIn(removedGroups);
                for (final var exercise : exercisesWithOutdatedGroups) {
                    // If the the user is still in another group for the exercise (TA -> INSTRUCTOR or INSTRUCTOR -> TA),
                    // then we have to add him as a member with the new access level
                    final var course = exercise.getCourseViaExerciseGroupOrCourseMember();
                    if (user.getGroups().contains(course.getInstructorGroupName())) {
                        gitlabApi.getGroupApi().updateMember(exercise.getProjectKey(), gitlabUser.getId(), MAINTAINER);
                    } else if (user.getGroups().contains(course.getEditorGroupName())) {
                        gitlabApi.getGroupApi().updateMember(exercise.getProjectKey(), gitlabUser.getId(), DEVELOPER);
                    } else if (user.getGroups().contains(course.getTeachingAssistantGroupName())) {
                        gitlabApi.getGroupApi().updateMember(exercise.getProjectKey(), gitlabUser.getId(), REPORTER);
                    } else {
                        // If the user is not a member of any relevant group any more, we can remove him from the exercise
                        try {
                            gitlabApi.getGroupApi().removeMember(exercise.getProjectKey(), gitlabUser.getId());
                        }
                        catch (GitLabApiException ex) {
                            // If user membership to group is missing on Gitlab, ignore the exception
                            // and let artemis synchronize with GitLab groups
                            if (ex.getHttpStatus() != 404) {
                                log.error("Gitlab Exception when removing a user " + gitlabUser.getId() + " to a group " + exercise.getProjectKey(), ex);
                            }
                        }
                    }
                }
            }
        }
        catch (GitLabApiException e) {
            throw new GitLabException("Error while trying to update user in GitLab: " + user, e);
        }
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
            if(userGroups != null) {
                if(userGroups.contains(oldTeachingAssistantGroup) || userGroups.contains(oldEditorGroup) || userGroups.contains(oldInstructorGroup)) {
                    oldUsers.add(user);
                } else {
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

        for (User user : newUsers) {
            Set<String> groups = user.getGroups();
            if(user.getGroups() != null) {
                final int userId = getUserId(user.getLogin());

                if (groups.contains(updatedCourse.getInstructorGroupName())) {
                    addUserToGroups(userId, programmingExercises, MAINTAINER);
                } else if (groups.contains(updatedCourse.getEditorGroupName())) {
                    addUserToGroups(userId, programmingExercises, DEVELOPER);
                } else if (groups.contains(updatedCourse.getTeachingAssistantGroupName())) {
                    addUserToGroups(userId, programmingExercises, REPORTER);
                } else {
                    removeMemberFromExercises(programmingExercises, user);
                }
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

        for (User user : oldUsers) {

            Set<String> groups = user.getGroups();
            if(user.getGroups() == null) {
                removeMemberFromExercises(programmingExercises, user);
            }

            if(groups.contains(updatedCourse.getInstructorGroupName())) {
                updateMemberExercisePermissions(programmingExercises, user, MAINTAINER);
            } else if (groups.contains(updatedCourse.getEditorGroupName())){
                updateMemberExercisePermissions(programmingExercises, user, DEVELOPER);
            } else if (groups.contains(updatedCourse.getTeachingAssistantGroupName())) {
                updateMemberExercisePermissions(programmingExercises, user, REPORTER);
            } else {
                removeMemberFromExercises(programmingExercises, user);
            }
        }

    }


    /**
     * Updates the permissions for the passed user to the passed accessLevel
     *
     * @param programmingExercises  all exercises for which the permissions shall be updated
     * @param user                  user for whom the permissions shall be updated
     * @param accessLevel           access level that shall be set for a user
     */
    private void updateMemberExercisePermissions(List<ProgrammingExercise> programmingExercises, User user, AccessLevel accessLevel) {
        final int userId = getUserId(user.getLogin());

        programmingExercises.forEach(exercise -> {
            try {
                gitlabApi.getGroupApi().updateMember(exercise.getProjectKey(), userId, accessLevel);
            } catch (GitLabApiException e) {
                throw new GitLabException("Error while updating GitLab group " + exercise.getProjectKey(), e);
            }
        });

    }

    private void removeMemberFromExercises(List<ProgrammingExercise> exercises, User user) {
        final int userId = getUserId(user.getLogin());

        exercises.forEach(exercise -> {
            try {
                gitlabApi.getGroupApi().removeMember(exercise.getProjectKey(), userId);
            } catch (GitLabApiException e) {
                throw new GitLabException("Error while updating GitLab group " + exercise.getProjectKey(), e);
            }
        });

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

    private int getUserIdCreateIfNotExists(User user) {
        try {
            var gitlabUser = gitlabApi.getUserApi().getUser(user.getLogin());
            if (gitlabUser == null) {
                gitlabUser = importUser(user);
            }

            return gitlabUser.getId();
        }
        catch (GitLabApiException e) {
            throw new GitLabException("Unable to get ID for user " + user.getLogin(), e);
        }
    }

    /**
     * adds a Gitlab user to a Gitlab group based on the provided exercises (project key) and the given access level
     *
     * @param userId      the Gitlab user id
     * @param exercises   the list of exercises which project key is used as the Gitlab "group" (i.e. Gitlab project)
     * @param accessLevel the access level that the user should get as part of the group/project
     */
    public void addUserToGroups(int userId, List<ProgrammingExercise> exercises, AccessLevel accessLevel) {
        for (final var exercise : exercises) {
            try {
                gitlabApi.getGroupApi().addMember(exercise.getProjectKey(), userId, accessLevel);
            }
            catch (GitLabApiException e) {
                if (e.getMessage().equals("Member already exists")) {
                    log.warn("Member already exists for group {}", exercise.getProjectKey());
                    return;
                }
                throw new GitLabException(String.format("Error adding new user [%d] to group [%s]", userId, exercise), e);
            }
        }
    }

    /**
     * creates a Gitlab user account based on the passed Artemis user account with the same email, login, name and password
     *
     * @param user a valid Artemis user (account)
     * @return a Gitlab user
     */
    public org.gitlab4j.api.models.User importUser(User user) {
        final var gitlabUser = new org.gitlab4j.api.models.User().withEmail(user.getEmail()).withUsername(user.getLogin()).withName(getUsersName(user)).withCanCreateGroup(false)
                .withCanCreateProject(false).withSkipConfirmation(true);
        try {
            return gitlabApi.getUserApi().createUser(gitlabUser, passwordService.decryptPassword(user), false);
        }
        catch (GitLabApiException e) {
            throw new GitLabException("Unable to create new user in GitLab " + user.getLogin(), e);
        }
    }

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
     * retrieves the user id of the Gitlab user with the given user name
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
