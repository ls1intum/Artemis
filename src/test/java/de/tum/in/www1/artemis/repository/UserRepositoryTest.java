package de.tum.in.www1.artemis.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.user.UserUtilService;

class UserRepositoryTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "userrepotest";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserUtilService userUtilService;

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
}
