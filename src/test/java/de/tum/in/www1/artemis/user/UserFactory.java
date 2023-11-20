package de.tum.in.www1.artemis.user;

import java.util.*;

import de.tum.in.www1.artemis.domain.Authority;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.service.dto.StudentDTO;

/**
 * Factory for creating Users and related objects.
 */
public class UserFactory {

    public static final String USER_PASSWORD = "00000000";

    /**
     * Generates the given amount of Users with the given arguments.
     *
     * @param loginPrefix        The prefix that will be added in front of every User's username
     * @param commonPasswordHash The password hash that will be set for every User
     * @param groups             The groups that the Users will be added to
     * @param authorities        The authorities that the Users will have
     * @param amount             The amount of Users to generate
     * @return The List of generated Users
     */
    public static List<User> generateActivatedUsers(String loginPrefix, String commonPasswordHash, String[] groups, Set<Authority> authorities, int amount) {
        List<User> generatedUsers = new ArrayList<>();
        for (int i = 1; i <= amount; i++) {
            User user = generateActivatedUser(loginPrefix + i, commonPasswordHash);
            if (groups != null) {
                user.setGroups(Set.of(groups));
                user.setAuthorities(authorities);
            }
            generatedUsers.add(user);
        }
        return generatedUsers;
    }

    /**
     * Generates the given amount of Users with the given arguments.
     *
     * @param loginPrefix The prefix that will be added in front of every User's username
     * @param groups      The groups that the Users will be added to
     * @param authorities The authorities that the Users will have
     * @param amount      The amount of Users to generate
     * @return The List of generated Users
     */
    public static List<User> generateActivatedUsers(String loginPrefix, String[] groups, Set<Authority> authorities, int amount) {
        return generateActivatedUsers(loginPrefix, USER_PASSWORD, groups, authorities, amount);
    }

    /**
     * Generates the given amount of Users with the given arguments.
     *
     * @param loginPrefix              The prefix that will be added in front of every User's username
     * @param groups                   The groups that the Users will be added to
     * @param authorities              The authorities that the Users will have
     * @param amount                   The amount of Users to generate
     * @param registrationNumberPrefix The prefix that will be added in front of every User's registration number
     * @return The List of generated Users
     */
    public static List<User> generateActivatedUsersWithRegistrationNumber(String loginPrefix, String[] groups, Set<Authority> authorities, int amount,
            String registrationNumberPrefix) {
        List<User> generatedUsers = generateActivatedUsers(loginPrefix, groups, authorities, amount);
        for (int i = 0; i < amount; i++) {
            generatedUsers.get(i).setRegistrationNumber(registrationNumberPrefix + "R" + i);
        }
        return generatedUsers;
    }

    /**
     * Generates a User with the given arguments.
     *
     * @param login    The username of the User
     * @param password The password of the User
     * @return The generated User
     */
    public static User generateActivatedUser(String login, String password) {
        User user = new User();
        user.setLogin(login);
        user.setPassword(password);
        user.setFirstName(login + "First");
        user.setLastName(login + "Last");
        user.setEmail(login + "@test.de");
        user.setActivated(true);
        user.setLangKey("en");
        user.setGroups(new HashSet<>());
        user.setAuthorities(new HashSet<>());
        return user;
    }

    /**
     * Generates a User with the given username.
     *
     * @param login The username of the User
     * @return The generated User
     */
    public static User generateActivatedUser(String login) {
        return generateActivatedUser(login, USER_PASSWORD);
    }

    /**
     * Generates a StudentDTO with the given registration number.
     *
     * @param registrationNumber The registration number of the User.
     * @return The generated StudentDTO
     */
    public static StudentDTO generateStudentDTOWithRegistrationNumber(String registrationNumber) {
        return new StudentDTO(null, null, null, registrationNumber, null);
    }
}
