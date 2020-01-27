package de.tum.in.www1.artemis.security;

import java.util.Optional;

import org.springframework.lang.Nullable;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;

public interface ArtemisAuthenticationProvider extends AuthenticationProvider {

    /**
     * Gets or creates the user object for the specified authentication.
     *
     * @param authentication the Spring authentication object which includes the username and password
     * @param firstName The first name of the user that should get created if not present
     * @param lastName The last name of the user that should get created if not present
     * @param email The email of the user that should get created if not present
     * @param skipPasswordCheck whether the password against the by the user management system provided user should be skipped
     * @return The Artemis user identified by the provided credentials
     */
    User getOrCreateUser(Authentication authentication, @Nullable String firstName, @Nullable String lastName, @Nullable String email, boolean skipPasswordCheck);

    /**
     * Adds a user to the specified group
     *
     * @param username The login name of the user
     * @param group The group the user should get added to
     */
    void addUserToGroup(String username, String group);

    /**
     * Removes a user from the specified group
     *
     * @param username The login of the user
     * @param group The groupname the user should get added to
     */
    void removeUserFromGroup(String username, String group);

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
     * Registers a user in a course by adding him to the student group of the course
     *
     * @param user The user that should get added to the course
     * @param course The course to which the user should get added to
     */
    // TODO why is this in the authentication provider? I think we should move this to the course service.
    void registerUserForCourse(User user, Course course);
}
