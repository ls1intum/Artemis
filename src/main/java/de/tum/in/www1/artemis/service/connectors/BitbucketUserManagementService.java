package de.tum.in.www1.artemis.service.connectors;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.exception.BitbucketException;
import de.tum.in.www1.artemis.exception.VersionControlException;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.connectors.bitbucket.BitbucketPermission;
import de.tum.in.www1.artemis.service.connectors.bitbucket.BitbucketService;
import de.tum.in.www1.artemis.service.user.PasswordService;

@Service
@Profile("bitbucket")
public class BitbucketUserManagementService implements VcsUserManagementService {

    private final Logger log = LoggerFactory.getLogger(BitbucketUserManagementService.class);

    private final BitbucketService bitbucketService;

    private final UserRepository userRepository;

    private final PasswordService passwordService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    public BitbucketUserManagementService(BitbucketService bitbucketService, UserRepository userRepository, PasswordService passwordService,
            ProgrammingExerciseRepository programmingExerciseRepository) {
        this.bitbucketService = bitbucketService;
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.programmingExerciseRepository = programmingExerciseRepository;
    }

    /**
     * Creates a new user in the VCS based on a local Artemis user. Should be called if Artemis handles user creation
     * and management
     *
     * @param user The local Artemis user, which will be available in the VCS after invoking this method
     */
    @Override
    public void createVcsUser(User user) throws VersionControlException {
        if (user.isInternal()) {
            // User
            if (!bitbucketService.userExists(user.getLogin())) {
                log.debug("Bitbucket user {} does not exist yet", user.getLogin());
                String displayName = user.getName().trim();
                bitbucketService.createUser(user.getLogin(), passwordService.decryptPasswordByLogin(user.getLogin()).get(), user.getEmail(), displayName);

                try {
                    // NOTE: we need to fetch the user here again to make sure that the groups are not lazy loaded.
                    User repoUser = userRepository.getUserWithGroupsAndAuthorities(user.getLogin());
                    bitbucketService.addUserToGroups(repoUser.getLogin(), repoUser.getGroups());
                }
                catch (BitbucketException e) {
                    /*
                     * This might throw exceptions, for example if the group does not exist on Bitbucket. We can safely ignore them.
                     */
                }
            }
            else {
                log.debug("Bitbucket user {} already exists", user.getLogin());
            }
        }
    }

    /**
     * Updates a new user in the VCS based on a local Artemis user. Should be called if Artemis handles user management.
     * This will change the following:
     * <ul>
     *     <li>Update the password of the user</li>
     *     <li>Update the groups the user belongs to, i.e. removing him from exercises that reference old groups</li>
     * </ul>
     * @param vcsLogin                  The username of the user in the VCS
     *
     * @param user                      The updated user in Artemis
     * @param removedGroups             groups that the user does not belong to any longer
     * @param addedGroups               The new groups the Artemis user got added to
     * @param shouldSynchronizePassword whether the password should be synchronized between Artemis and the VcsUserManagementService
     */
    @Override
    public void updateVcsUser(String vcsLogin, User user, Set<String> removedGroups, Set<String> addedGroups, boolean shouldSynchronizePassword) {
        bitbucketService.updateUserDetails(vcsLogin, user.getEmail(), user.getName());
        if (user.isInternal() && shouldSynchronizePassword) {
            bitbucketService.updateUserPassword(vcsLogin, passwordService.decryptPassword(user));
        }
        if (addedGroups != null && !addedGroups.isEmpty()) {
            bitbucketService.addUserToGroups(user.getLogin(), addedGroups);
        }
        if (removedGroups != null && !removedGroups.isEmpty()) {
            bitbucketService.removeUserFromGroups(user.getLogin(), removedGroups);
        }
    }

    /**
     * Deletes the user under the specified login from the VCS
     *
     * @param login The login of the user that should get deleted
     */
    @Override
    public void deleteVcsUser(String login) throws VersionControlException {
        bitbucketService.deleteAndEraseUser(login);
    }

    /**
     * Activates the VCS user.
     *
     * @param login The username of the user in the VCS
     * @throws VersionControlException if an exception occurred
     */
    @Override
    public void activateUser(String login) throws VersionControlException {
        // Not supported by Bitbucket
    }

    /**
     * Deactivates the VCS user.
     *
     * @param login The username of the user in the VCS
     * @throws VersionControlException if an exception occurred
     */
    @Override
    public void deactivateUser(String login) throws VersionControlException {
        // Not supported by Bitbucket
    }

    /**
     * Updates all exercises in a course based on the new instructors, editors and teaching assistant groups. This entails removing
     * all users from exercises, that are no longer part of any relevant group and adding all users to exercises in the course
     * that are part of the updated groups.
     *
     * @param updatedCourse             The updated course with the new permissions
     * @param oldInstructorGroup        The old instructor group name
     * @param oldEditorGroup            The old editor group name
     * @param oldTeachingAssistantGroup The old teaching assistant group name
     */
    @Override
    public void updateCoursePermissions(Course updatedCourse, String oldInstructorGroup, String oldEditorGroup, String oldTeachingAssistantGroup) {
        if (oldInstructorGroup.equals(updatedCourse.getInstructorGroupName()) && oldEditorGroup.equals(updatedCourse.getEditorGroupName())
                && oldTeachingAssistantGroup.equals(updatedCourse.getTeachingAssistantGroupName())) {
            // Do nothing if the group names didn't change
            return;
        }

        final List<ProgrammingExercise> programmingExercises = programmingExerciseRepository.findAllProgrammingExercisesInCourseOrInExamsOfCourse(updatedCourse);
        log.info("Update Bitbucket permissions for programming exercises: " + programmingExercises.stream().map(ProgrammingExercise::getProjectKey).toList());

        for (ProgrammingExercise programmingExercise : programmingExercises) {
            if (!oldInstructorGroup.equals(updatedCourse.getInstructorGroupName())) {
                bitbucketService.grantGroupPermissionToProject(programmingExercise.getProjectKey(), oldInstructorGroup, null);
                bitbucketService.grantGroupPermissionToProject(programmingExercise.getProjectKey(), updatedCourse.getInstructorGroupName(), BitbucketPermission.PROJECT_ADMIN);
            }
            if (!oldEditorGroup.equals(updatedCourse.getEditorGroupName())) {
                bitbucketService.grantGroupPermissionToProject(programmingExercise.getProjectKey(), oldEditorGroup, null);
                bitbucketService.grantGroupPermissionToProject(programmingExercise.getProjectKey(), updatedCourse.getEditorGroupName(), BitbucketPermission.PROJECT_WRITE);
            }
            if (!oldEditorGroup.equals(updatedCourse.getTeachingAssistantGroupName())) {
                bitbucketService.grantGroupPermissionToProject(programmingExercise.getProjectKey(), oldTeachingAssistantGroup, null);
                if (programmingExercise.isCourseExercise() || programmingExercise.getExerciseGroup().getExam().isAfterLatestStudentExamEnd()) {
                    bitbucketService.grantGroupPermissionToProject(programmingExercise.getProjectKey(), updatedCourse.getTeachingAssistantGroupName(),
                            BitbucketPermission.PROJECT_READ);
                }
            }
        }
    }
}
