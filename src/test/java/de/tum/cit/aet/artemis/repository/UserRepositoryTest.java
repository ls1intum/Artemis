package de.tum.cit.aet.artemis.repository;

import static de.tum.cit.aet.artemis.user.UserFactory.USER_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.core.domain.Authority;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.service.user.PasswordService;
import de.tum.cit.aet.artemis.user.UserUtilService;

class UserRepositoryTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "userrepotest";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private PasswordService passwordService;

    private List<User> users;

    @BeforeEach
    void setUp() {
        users = userUtilService.addUsers(TEST_PREFIX, 3, 1, 1, 1);
    }

    @Test
    void testFindWithMissingAccessToken() {
        users.forEach(user -> user.setVcsAccessToken(null));
        users = userRepository.saveAll(users);

        final Set<User> usersMissingTokens = userRepository.getUsersWithAccessTokenNull();

        assertThat(usersMissingTokens).containsAll(users);
    }

    @Test
    void testFindWithExpiredTokensIgnoreUsersWithoutTokens() {
        users.forEach(user -> user.setVcsAccessToken(null));
        users = userRepository.saveAll(users);

        final Set<User> usersMissingTokens = userRepository.getUsersWithAccessTokenExpirationDateBefore(ZonedDateTime.now());

        assertThat(usersMissingTokens).doesNotContainAnyElementsOf(users);
    }

    @Test
    void testFindWithExpiredTokens() {
        users.forEach(user -> {
            user.setVcsAccessToken("valid-token");
            user.setVcsAccessTokenExpiryDate(ZonedDateTime.now().minusDays(2));
        });
        users = userRepository.saveAll(users);

        final Set<User> usersExpiredTokens = userRepository.getUsersWithAccessTokenExpirationDateBefore(ZonedDateTime.now());

        assertThat(usersExpiredTokens).containsAll(users);
    }

    @Test
    void testFindWithTokensExpiredBefore() {
        users.forEach(user -> {
            user.setVcsAccessToken("valid-token");
            user.setVcsAccessTokenExpiryDate(ZonedDateTime.now().plusDays(2).plusHours(23));
        });
        users = userRepository.saveAll(users);

        final ZonedDateTime deadline = ZonedDateTime.now().plusDays(3);
        final Set<User> usersExpiredTokens = userRepository.getUsersWithAccessTokenExpirationDateBefore(deadline);

        assertThat(usersExpiredTokens).containsAll(users);
    }

    @Test
    void testFindWithNotYetExpiredTokensIgnored() {
        users.forEach(user -> {
            user.setVcsAccessToken("valid-token");
            user.setVcsAccessTokenExpiryDate(ZonedDateTime.now().plusHours(2));
        });
        users = userRepository.saveAll(users);

        final Set<User> usersNotYetExpiredTokens = userRepository.getUsersWithAccessTokenExpirationDateBefore(ZonedDateTime.now());

        assertThat(usersNotYetExpiredTokens).doesNotContainAnyElementsOf(users);
    }

    @Test
    void testFindAllNotEnrolledUsers() {
        List<User> expected = userRepository
                .saveAll(userUtilService.generateActivatedUsers(TEST_PREFIX, passwordService.hashPassword(USER_PASSWORD), new String[] {}, Set.of(), 1, 3));
        // Should not find administrators
        List<User> unexpected = userRepository.saveAll(
                userUtilService.generateActivatedUsers(TEST_PREFIX, passwordService.hashPassword(USER_PASSWORD), new String[] {}, Set.of(Authority.ADMIN_AUTHORITY), 4, 4));
        // Should not find deleted users
        List<User> deleted = userUtilService.generateActivatedUsers(TEST_PREFIX, passwordService.hashPassword(USER_PASSWORD), new String[] {}, Set.of(), 5, 6);
        deleted.forEach(user -> user.setDeleted(true));
        unexpected.addAll(userRepository.saveAll(deleted));

        final List<String> actual = userRepository.findAllNotEnrolledUsers();

        assertThat(actual).doesNotContainAnyElementsOf(unexpected.stream().map(User::getLogin).toList());
        assertThat(actual).containsAll(expected.stream().map(User::getLogin).toList());
    }
}
