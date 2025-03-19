package de.tum.cit.aet.artemis.core.user;

import static de.tum.cit.aet.artemis.core.user.util.UserFactory.USER_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.core.domain.Authority;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.service.user.PasswordService;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class UserRepositoryTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "userrepotest";

    @Autowired
    private UserTestRepository userRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private PasswordService passwordService;

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
