package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.programming.repository.UserVCSAccessTokenRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * The personal VCS access token lives in a table of its own, so an account without a token has no row at all rather
 * than a row of nulls. These tests pin that "no row" reads as "no token" everywhere, which is where a missed default
 * would silently grant or deny repository access.
 */
class UserVcsAccessTokenServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "uservcstoken";

    @Autowired
    private UserVcsAccessTokenService userVcsAccessTokenService;

    @Autowired
    private UserVCSAccessTokenRepository userVcsAccessTokenRepository;

    @Autowired
    private UserTestRepository userRepository;

    @Autowired
    private UserUtilService userUtilService;

    private User user;

    @BeforeEach
    void setUp() {
        user = userUtilService.createAndSaveUser(TEST_PREFIX + "student1");
    }

    @Test
    void anAccountWithoutARowHasNoToken() {
        assertThat(userVcsAccessTokenService.findToken(user.getId())).isNull();
        assertThat(userVcsAccessTokenService.findExpiryDate(user.getId())).isNull();
        assertThat(userVcsAccessTokenService.hasUsableToken(user.getId())).isFalse();
        assertThat(userVcsAccessTokenService.findUsableToken(user.getId())).isEmpty();
    }

    @Test
    void aStoredTokenIsFoundAndUsable() {
        ZonedDateTime expiry = ZonedDateTime.now().plusDays(30);
        userVcsAccessTokenService.store(user.getId(), "token-value", expiry);

        assertThat(userVcsAccessTokenService.findToken(user.getId())).isEqualTo("token-value");
        assertThat(userVcsAccessTokenService.findExpiryDate(user.getId())).isNotNull();
        assertThat(userVcsAccessTokenService.hasUsableToken(user.getId())).isTrue();
        assertThat(userVcsAccessTokenService.findUsableToken(user.getId())).isPresent();
    }

    /**
     * An expired token must not authenticate. findUsableToken filters it out so that a caller comparing a presented
     * secret cannot accept one by forgetting to check the date itself.
     */
    @Test
    void anExpiredTokenIsNotUsableButIsStillFound() {
        userVcsAccessTokenService.store(user.getId(), "token-value", ZonedDateTime.now().minusDays(1));

        assertThat(userVcsAccessTokenService.findToken(user.getId())).as("still readable, e.g. for the account page").isEqualTo("token-value");
        assertThat(userVcsAccessTokenService.hasUsableToken(user.getId())).isFalse();
        assertThat(userVcsAccessTokenService.findUsableToken(user.getId())).isEmpty();
    }

    @Test
    void storingTwiceReplacesTheTokenRatherThanAddingARow() {
        userVcsAccessTokenService.store(user.getId(), "first", ZonedDateTime.now().plusDays(1));
        userVcsAccessTokenService.store(user.getId(), "second", ZonedDateTime.now().plusDays(2));

        assertThat(userVcsAccessTokenService.findToken(user.getId())).isEqualTo("second");
        assertThat(userVcsAccessTokenRepository.findAll()).filteredOn(token -> token.getUserId() == user.getId()).hasSize(1);
    }

    /**
     * Revocation deletes the row rather than nulling the columns, so that "no row" stays the single representation of
     * "no token".
     */
    @Test
    void revokingRemovesTheRow() {
        userVcsAccessTokenService.store(user.getId(), "token-value", ZonedDateTime.now().plusDays(1));

        userVcsAccessTokenService.revoke(user.getId());

        assertThat(userVcsAccessTokenRepository.findByUserId(user.getId())).isEmpty();
        assertThat(userVcsAccessTokenService.findToken(user.getId())).isNull();
    }

    @Test
    void revokingAnAccountWithoutATokenIsANoOp() {
        userVcsAccessTokenService.revoke(user.getId());

        assertThat(userVcsAccessTokenRepository.findByUserId(user.getId())).isEmpty();
    }

    @Test
    void theExpiryWindowLookupFindsOnlyTokensInsideIt() {
        User other = userUtilService.createAndSaveUser(TEST_PREFIX + "student2");
        userVcsAccessTokenService.store(user.getId(), "inside", ZonedDateTime.now().plusDays(3));
        userVcsAccessTokenService.store(other.getId(), "outside", ZonedDateTime.now().plusDays(90));

        var found = userVcsAccessTokenService.findUserIdsWithTokenExpiringBetween(ZonedDateTime.now(), ZonedDateTime.now().plusDays(7));

        assertThat(found).contains(user.getId()).doesNotContain(other.getId());
    }
}
