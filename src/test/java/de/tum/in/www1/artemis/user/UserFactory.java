package de.tum.in.www1.artemis.user;

import java.util.*;

import de.tum.in.www1.artemis.domain.Authority;
import de.tum.in.www1.artemis.domain.User;

/**
 * Factory for creating Users and related objects.
 */
public class UserFactory {

    public static final String USER_PASSWORD = "00000000";

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

    public static List<User> generateActivatedUsers(String loginPrefix, String[] groups, Set<Authority> authorities, int amount) {
        return generateActivatedUsers(loginPrefix, USER_PASSWORD, groups, authorities, amount);
    }

    /**
     * Generate users that have registration numbers
     *
     * @param loginPrefix              prefix that will be added in front of every user's login
     * @param groups                   groups that the users will be added
     * @param authorities              authorities that the users will have
     * @param amount                   amount of users to generate
     * @param registrationNumberPrefix prefix that will be added in front of every user
     * @return users that were generated
     */
    public static List<User> generateActivatedUsersWithRegistrationNumber(String loginPrefix, String[] groups, Set<Authority> authorities, int amount,
            String registrationNumberPrefix) {
        List<User> generatedUsers = generateActivatedUsers(loginPrefix, groups, authorities, amount);
        for (int i = 0; i < amount; i++) {
            generatedUsers.get(i).setRegistrationNumber(registrationNumberPrefix + "R" + i);
        }
        return generatedUsers;
    }

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

    public static User generateActivatedUser(String login) {
        return generateActivatedUser(login, USER_PASSWORD);
    }
}
