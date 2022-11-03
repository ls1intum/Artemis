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
import de.tum.in.www1.artemis.service.connectors.localgit.LocalGitService;
import de.tum.in.www1.artemis.service.user.PasswordService;

@Service
@Profile("localgit")
public class LocalGitUserManagementService implements VcsUserManagementService {

    private final Logger log = LoggerFactory.getLogger(LocalGitUserManagementService.class);

    private final LocalGitService localGitService;

    private final UserRepository userRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    public LocalGitUserManagementService(LocalGitService localGitService, UserRepository userRepository, PasswordService passwordService,
                                         ProgrammingExerciseRepository programmingExerciseRepository) {
        this.localGitService = localGitService;
        this.userRepository = userRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
    }

    /**
     * Creates a new user in the VCS based on a local Artemis user. Should be called
     * if Artemis handles user creation
     * and management
     *
     * @param user     The local Artemis user, which will be available in the VCS
     *                 after invoking this method
     * @param password the password of the user to be set
     */
    @Override
    public void createVcsUser(User user, String password) throws VersionControlException {
        // Not implemented.
    }

    /**
     * Updates a new user in the VCS based on a local Artemis user. Should be called
     * if Artemis handles user management.
     * This will change the following:
     * <ul>
     * <li>Update the password of the user</li>
     * <li>Update the groups the user belongs to, i.e. removing him from exercises
     * that reference old groups</li>
     * </ul>
     *
     * @param vcsLogin      The username of the user in the VCS
     * @param user          The updated user in Artemis
     * @param removedGroups groups that the user does not belong to any longer
     * @param addedGroups   The new groups the Artemis user got added to
     * @param newPassword   The new password, null otherwise
     */
    @Override
    public void updateVcsUser(String vcsLogin, User user, Set<String> removedGroups, Set<String> addedGroups, String newPassword) {
        // Not implemented.
    }

    /**
     * Deletes the user under the specified login from the VCS
     *
     * @param login The login of the user that should get deleted
     */
    @Override
    public void deleteVcsUser(String login) throws VersionControlException {
        // Not implemented.
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
     * Updates all exercises in a course based on the new instructors, editors and
     * teaching assistant groups. This entails removing
     * all users from exercises, that are no longer part of any relevant group and
     * adding all users to exercises in the course
     * that are part of the updated groups.
     *
     * @param updatedCourse             The updated course with the new permissions
     * @param oldInstructorGroup        The old instructor group name
     * @param oldEditorGroup            The old editor group name
     * @param oldTeachingAssistantGroup The old teaching assistant group name
     */
    @Override
    public void updateCoursePermissions(Course updatedCourse, String oldInstructorGroup, String oldEditorGroup, String oldTeachingAssistantGroup) {
        // Not implemented.
        }
}
