package de.tum.cit.aet.artemis.service.connectors.vcs;

import java.util.Set;

import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.exception.VersionControlException;

public interface VcsUserManagementService {

    /**
     * Creates a new user in the VCS based on a local Artemis user. Should be called if Artemis handles user creation
     * and management
     *
     * @param user     The local Artemis user, which will be available in the VCS after invoking this method
     * @param password the password of the user to be set
     */
    void createVcsUser(User user, String password) throws VersionControlException;

    /**
     * Updates a new user in the VCS based on a local Artemis user. Should be called if Artemis handles user management.
     * This will change the following:
     * <ul>
     * <li>Update the password of the user</li>
     * <li>Update the groups the user belongs to, i.e. removing him from exercises that reference old groups</li>
     * </ul>
     *
     * @param vcsLogin      The username of the user in the VCS
     * @param user          The updated user in Artemis
     * @param removedGroups groups that the user does not belong to any longer
     * @param addedGroups   The new groups the Artemis user got added to
     * @param newPassword   if set, the password gets updated
     */
    void updateVcsUser(String vcsLogin, User user, Set<String> removedGroups, Set<String> addedGroups, String newPassword);

    default void updateVcsUser(String vcsLogin, User user, Set<String> removedGroups, Set<String> addedGroups) {
        updateVcsUser(vcsLogin, user, removedGroups, addedGroups, null);
    }

    /**
     * Deletes the user under the specified login from the VCS
     *
     * @param login The login of the user that should get deleted
     */
    void deleteVcsUser(String login) throws VersionControlException;

    /**
     * Activates the VCS user.
     *
     * @param login The username of the user in the VCS
     * @throws VersionControlException if an exception occurred
     */
    void activateUser(String login) throws VersionControlException;

    /**
     * Deactivates the VCS user.
     *
     * @param login The username of the user in the VCS
     * @throws VersionControlException if an exception occurred
     */
    void deactivateUser(String login) throws VersionControlException;

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
    void updateCoursePermissions(Course updatedCourse, String oldInstructorGroup, String oldEditorGroup, String oldTeachingAssistantGroup);
}
