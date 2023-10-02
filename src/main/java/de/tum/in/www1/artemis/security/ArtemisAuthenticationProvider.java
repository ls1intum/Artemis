package de.tum.in.www1.artemis.security;

import java.util.Optional;

import org.springframework.security.authentication.AuthenticationProvider;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.service.connectors.ConnectorHealth;

public interface ArtemisAuthenticationProvider extends AuthenticationProvider {

    /**
     * Adds a user to the specified group
     *
     * @param user  the user
     * @param group The group the user should get added to
     */
    void addUserToGroup(User user, String group);

    /**
     * creates the given user in the external user management (in case it is used)
     *
     * @param user the user that should be created
     */
    void createUserInExternalUserManagement(User user);

    /**
     * Removes a user from the specified group
     *
     * @param user  the user
     * @param group The groupname the user should get added to
     */
    void removeUserFromGroup(User user, String group);

    /**
     * Searches for a user with the given email address.
     *
     * @param email The email address to search for
     * @return An Optional containing the username if there is a user with the provided email, an empty Optional otherwise
     */
    Optional<String> getUsernameForEmail(String email);

    /**
     * Checks if the group can be used e.g. for specifying it in a course. Depending on the used authentication and
     * group management, some groups cannot be used unless they are also present in the external group management system.
     * This would be the case for e.g. Jira+Bitbucket+Bamboo, where groups also have to exist in those three systems if
     * we want to consistently use the in Artemis.
     *
     * @param group The name of the group for which the availability should get checked
     * @return True, if the group is available for usage, false otherwise
     */
    boolean isGroupAvailable(String group);

    /**
     * Checks if the underlying user management server is up and running and gives some additional information about the running
     * services if available
     *
     * @return The health of the user management service containing if it is up and running and any additional data, or the throwing exception otherwise
     */
    ConnectorHealth health();

    /**
     * create a group with the given name
     *
     * @param groupName the name of the group which should be created
     */
    void createGroup(String groupName);

    /**
     * delete the group with the given name
     *
     * @param groupName the name of the group which should be deleted
     */
    void deleteGroup(String groupName);
}
