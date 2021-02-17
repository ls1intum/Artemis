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
     * @throws ContinuousIntegrationException thrown when a job cannot be fetched/updated
     */
    void createUser(User user) throws ContinuousIntegrationException;

    /**
     * Deletes the user under the specified login from the CIS.
     *
     * @param userLogin The login of the user that should be deleted
     * @throws ContinuousIntegrationException thrown when a job cannot be fetched/updated
     */
    void deleteUser(String userLogin) throws ContinuousIntegrationException;

    /**
     * Updates the user in the CIS with the data from the Artemis user. Throws
     * an exceptions if the user doesn't exist in the CIS.
     *
     * @param user The Artemis user
     * @throws ContinuousIntegrationException thrown when a job cannot be fetched/updated
     */
    void updateUser(User user) throws ContinuousIntegrationException;

    /**
     * Updates the user in the CIS with the data from the Artemis users. Also adds/removes
     * the user to/from the specified groups. Throws an exceptions if the user doesn't exist
     * in the CIS.
     *
     * @param user the Artemis user
     * @param groupsToAdd groups to add the user to
     * @param groupsToRemove groups to remove the user from
     * @throws ContinuousIntegrationException thrown when a job cannot be fetched/updated
     */
    void updateUserAndGroups(User user, Set<String> groupsToAdd, Set<String> groupsToRemove) throws ContinuousIntegrationException;

    /**
     * Adds the user to the specified group in the CIS. Groups define who has access
     * to what programming exercises.
     *
     * @param user The Artemis user to add to the group
     * @param group The group
     * @throws ContinuousIntegrationException thrown when a job cannot be fetched/updated
     */
    void addUserToGroups(User user, Set<String> group) throws ContinuousIntegrationException;

    /**
     * Removes the user from the specified group in the CIS. This e.g revokes access
     * to certain programming exericses.
     *
     * @param user The Artemis user to remove from the group
     * @param group The group
     * @throws ContinuousIntegrationException thrown when a job cannot be fetched/updated
     */
    void removeUserFromGroups(User user, Set<String> group) throws ContinuousIntegrationException;

    /**
     * Update permissions of all users that belong to the teaching assistant and instructor groups of the course. This
     * means removing each user from the old groups and adding them to the new one from the course.
     *
     * @param updatedCourse the course that has the new groups
     * @param oldInstructorGroup the old instructor group. Permissions are revoked for each user in that group
     * @param oldTeachingAssistantGroup the old teaching assistant group. Permissions are revoked for each user in that group
     * @throws ContinuousIntegrationException thrown when a job cannot be fetched/updated
     */
    void updateCoursePermissions(Course updatedCourse, String oldInstructorGroup, String oldTeachingAssistantGroup) throws ContinuousIntegrationException;
}
