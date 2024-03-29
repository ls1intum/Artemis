package de.tum.in.www1.artemis.service.connectors.vcs;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;

class VcsTokenRenewalServiceTest {

    private VcsTokenManagementService vcsTokenManagementService;

    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        vcsTokenManagementService = Mockito.mock(VcsTokenManagementService.class);
        userRepository = Mockito.mock(UserRepository.class);
    }

    @AfterEach
    void tearDown() {
        Mockito.reset(vcsTokenManagementService, userRepository);
    }

    @Test
    void testDoNothingIfNoTokenRequiredInServerConfig() {
        final VcsTokenRenewalService vcsTokenRenewalService = new VcsTokenRenewalService(false, Optional.of(vcsTokenManagementService), userRepository);

        vcsTokenRenewalService.renewAllVcsAccessTokens();

        verifyUserRepositoryNotCalled();
        verifyVcsTokenManagementServiceNotCalled();
    }

    @Test
    void testDoNothingIfNoTokenManagementService() {
        final VcsTokenRenewalService vcsTokenRenewalService = new VcsTokenRenewalService(false, Optional.empty(), userRepository);

        vcsTokenRenewalService.renewAllVcsAccessTokens();

        verifyUserRepositoryNotCalled();
        verifyVcsTokenManagementServiceNotCalled();
    }

    @ParameterizedTest
    @MethodSource("getRenewCounts")
    void testRenewAllPersonalAccessTokens(int expectedRenewalCount, int expectedCreationCount) {
        final VcsTokenRenewalService vcsTokenRenewalService = new VcsTokenRenewalService(true, Optional.of(vcsTokenManagementService), userRepository);
        final Users users = setUpMocking(expectedRenewalCount, expectedCreationCount);

        vcsTokenRenewalService.renewAllVcsAccessTokens();

        verifyUserRepositoryMocks();
        verifyVcsTokenManagementServiceMocks(users);
    }

    private static Stream<Arguments> getRenewCounts() {
        return Stream.of(Arguments.of(2, 3), Arguments.of(0, 4), Arguments.of(3, 0));
    }

    private Users setUpMocking(int expectedRenewalCount, int expectedCreationCount) {
        final Users users = generateUsers(expectedRenewalCount, expectedCreationCount);

        doReturn(users.withTokenRenewal()).when(userRepository).getUsersWithAccessTokenExpirationDateBefore(any());
        doReturn(users.withTokenCreation()).when(userRepository).getUsersWithAccessTokenNull();

        return users;
    }

    private void verifyUserRepositoryMocks() {
        verify(userRepository, times(1)).getUsersWithAccessTokenExpirationDateBefore(any());
        verify(userRepository, times(1)).getUsersWithAccessTokenNull();
    }

    private void verifyUserRepositoryNotCalled() {
        verify(userRepository, never()).getUsersWithAccessTokenExpirationDateBefore(any());
        verify(userRepository, never()).getUsersWithAccessTokenNull();
    }

    private void verifyVcsTokenManagementServiceMocks(final Users users) {
        for (final User user : users.withTokenCreation()) {
            verify(vcsTokenManagementService, times(1)).createAccessToken(user);
        }
        for (final User user : users.withTokenRenewal()) {
            verify(vcsTokenManagementService, times(1)).renewAccessToken(user);
        }
    }

    private void verifyVcsTokenManagementServiceNotCalled() {
        verify(vcsTokenManagementService, never()).createAccessToken(any());
        verify(vcsTokenManagementService, never()).renewAccessToken(any());
    }

    private Users generateUsers(int forTokenRenewal, int forTokenCreation) {
        final Set<User> forRenewal = generateUsers(forTokenRenewal).peek(user -> {
            user.setVcsAccessTokenExpiryDate(ZonedDateTime.now());
            user.setVcsAccessToken("existing-token");
        }).collect(Collectors.toUnmodifiableSet());
        final Set<User> forCreation = generateUsers(forTokenCreation).peek(user -> {
            user.setVcsAccessTokenExpiryDate(null);
            user.setVcsAccessToken(null);
        }).collect(Collectors.toUnmodifiableSet());

        return new Users(forRenewal, forCreation);
    }

    private Stream<User> generateUsers(int count) {
        return IntStream.range(0, count).mapToObj(idx -> {
            final User user = new User();
            user.setLogin(UUID.randomUUID().toString());
            return user;
        });
    }

    private record Users(Set<User> withTokenRenewal, Set<User> withTokenCreation) {
    }
}
