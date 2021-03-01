package de.tum.in.www1.artemis.service.connectors;

import java.util.Set;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;

public interface VcsUserManagementService {

    /**
     * Creates a new user in the VCS based on a local Artemis user. Should be called if Artemis handles user creation
     * and management
     *
     * @param user The local Artemis user, which will be available in the VCS after invoking this method
     */
    void createVcsUser(User user);

    /**
     * Updates a new user in the VCS based on a local Artemis user. Should be called if Artemis handles user management.
     * This will change the following:
     * <ul>
     *     <li>Update the password of the user</li>
     *     <li>Update the groups the user belongs to, i.e. removing him from exercises that reference old groups</li>
     * </ul>
     *
     * @param vcsLogin                  The username of the user in the VCS
     * @param user                      The updated user in Artemis
     * @param removedGroups             groups that the user does not belong to any longer
     * @param addedGroups               The new groups the Artemis user got added to
     * @param shouldSynchronizePassword whether the password should be synchronized between Artemis and the VcsUserManagementService
     */
    void updateVcsUser(String vcsLogin, User user, Set<String> removedGroups, Set<String> addedGroups, boolean shouldSynchronizePassword);

    /**
     * Deletes the user under the specified login from the VCS
     *
     * @param login The login of the user that should get deleted
     */
    void deleteVcsUser(String login);

    /**
     * Updates all exercises in a course based on the new instructors and teaching assistant groups. This entails removing
     * all users from exercises, that are no longer part of any relevant group and adding all users to exercises in the course
     * that are part of the updated groups.
     *
     * @param updatedCourse             The updated course with the new permissions
     * @param oldInstructorGroup        The old instructor group name
     * @param oldTeachingAssistantGroup The old teaching assistant group name
     */
    void updateCoursePermissions(Course updatedCourse, String oldInstructorGroup, String oldTeachingAssistantGroup);
}
