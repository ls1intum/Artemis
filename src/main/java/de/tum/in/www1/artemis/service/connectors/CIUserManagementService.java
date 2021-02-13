package de.tum.in.www1.artemis.service.connectors;

import java.util.Set;

import de.tum.in.www1.artemis.domain.User;

public interface CIUserManagementService {

    /**
     * Creates a new user in the CIS based the Artemis user.
     *
     * @param user The Artemis user
     */
    void createUser(User user);

    /**
     * Deletes the user under the specified login from the CIS.
     *
     * @param userLogin The login of the user that should be deleted
     */
    void deleteUser(String userLogin);

    /**
     * Updates the user in the CIS with the data from the Artemis user. Throws
     * an exceptions if the user doesn't exist in the CIS.
     *
     * @param user The Artemis user
     */
    void updateUser(User user);

    /**
     * Updates the user in the CIS with the data from the Artemis users. Also adds/removes
     * the user to/from the specified groups. Throws an exceptions if the user doesn't exist
     * in the CIS.
     *
     * @param user the Artemis user
     * @param groupsToAdd groups to add the user to
     * @param groupsToRemove groups to remove the user from
     */
    void updateUserAndGroups(User user, Set<String> groupsToAdd, Set<String> groupsToRemove);

    /**
     * Adds the user to the specified group in the CIS. Groups define who has access
     * to what programming exercises.
     *
     * @param user The Artemis user to add to the group
     * @param group The group
     */
    void addUserToGroups(User user, Set<String> group);

    /**
     * Removes the user from the specified group in the CIS. This e.g revokes access
     * to certain programming exericses.
     *
     * @param user The Artemis user to remove from the group
     * @param group The group
     */
    void removeUserFromGroups(User user, Set<String> group);

    /**
     * Updates the user in the CIS with the data from the Artemis user. Creates
     * a new CIS user if it doesn't exist.
     *
     * @param user The Artemis user
     */
    void updateOrCreateUser(User user);
}
