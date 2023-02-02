package de.tum.in.www1.artemis.service.connectors;

import java.util.Set;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.exception.ContinuousIntegrationException;

public interface CIUserManagementService {

    /**
     * Creates a new user in the CIS based the Artemis user.
     *
     * @param user The Artemis user
     * @param password The user's password
     * @throws ContinuousIntegrationException thrown when a job cannot be fetched/updated
     */
    void createUser(User user, String password) throws ContinuousIntegrationException;

    /**
     * Deletes the user under the specified login from the CIS.
     *
     * @param user The user that should be deleted
     * @throws ContinuousIntegrationException thrown when a job cannot be fetched/updated
     */
    void deleteUser(User user) throws ContinuousIntegrationException;

    /**
     * Updates the user in the CIS with the data from the Artemis user. Throws
     * an exceptions if the user doesn't exist in the CIS.
     *
     * @param user The Artemis user
     * @param password The user's password
     * @throws ContinuousIntegrationException thrown when a job cannot be fetched/updated
     */
    void updateUser(User user, String password) throws ContinuousIntegrationException;

    /**
     * Updates the user login of the user.
     *
     * @param oldLogin the old login
     * @param user The Artemis user with the new login
     * @param password The user's password
     */
    void updateUserLogin(String oldLogin, User user, String password) throws ContinuousIntegrationException;

    /**
     * Updates the user in the CIS with the data from the Artemis users. Also adds/removes
     * the user to/from the specified groups. Throws an exception if the user doesn't exist
     * in the CIS.
     *
     * @param oldLogin the old login if it was updated
     * @param user the Artemis user
     * @param password the user's password
     * @param groupsToAdd groups to add the user to
     * @param groupsToRemove groups to remove the user from
     * @throws ContinuousIntegrationException thrown when a job cannot be fetched/updated
     */
    void updateUserAndGroups(String oldLogin, User user, String password, Set<String> groupsToAdd, Set<String> groupsToRemove) throws ContinuousIntegrationException;

    /**
     * Adds the user to the specified group in the CIS. Groups define who has access
     * to what programming exercises.
     *
     * @param userLogin The user login of the Artemis user to add to the group
     * @param group The group
     * @throws ContinuousIntegrationException thrown when a job cannot be fetched/updated
     */
    void addUserToGroups(String userLogin, Set<String> group) throws ContinuousIntegrationException;

    /**
     * Removes the user from the specified group in the CIS. This e.g. revokes access
     * to certain programming exercises.
     *
     * @param userLogin The user login of the Artemis user to remove from the group
     * @param group The group
     * @throws ContinuousIntegrationException thrown when a job cannot be fetched/updated
     */
    void removeUserFromGroups(String userLogin, Set<String> group) throws ContinuousIntegrationException;

    /**
     * Update permissions of all users that belong to the teaching assistant and instructor groups of the course. This
     * means removing each user from the old groups and adding them to the new one from the course.
     *
     * @param updatedCourse the course that has the new groups
     * @param oldInstructorGroup the old instructor group. Permissions are revoked for each user in that group
     * @param oldEditorGroup the old editor group. Permissions are revoked for each user in that group
     * @param oldTeachingAssistantGroup the old teaching assistant group. Permissions are revoked for each user in that group
     * @throws ContinuousIntegrationException thrown when a job cannot be fetched/updated
     */
    void updateCoursePermissions(Course updatedCourse, String oldInstructorGroup, String oldEditorGroup, String oldTeachingAssistantGroup) throws ContinuousIntegrationException;
}
