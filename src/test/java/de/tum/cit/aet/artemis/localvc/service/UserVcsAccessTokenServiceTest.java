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
        assertThat(userVcsAccessTokenService.findUsableToken(user.getId())).isEmpty();
    }

    @Test
    void aTokenThatExistsIsFoundAndUsable() {
        ZonedDateTime expiry = ZonedDateTime.now().plusDays(30);
        userUtilService.setUserVcsAccessTokenAndExpiryDateAndSave(user, "token-value", expiry);

        assertThat(userVcsAccessTokenService.findToken(user.getId())).isEqualTo("token-value");
        assertThat(userVcsAccessTokenService.findExpiryDate(user.getId())).isNotNull();
        assertThat(userVcsAccessTokenService.findUsableToken(user.getId())).isPresent();
    }

    /**
     * An expired token must not authenticate. findUsableToken filters it out so that a caller comparing a presented
     * secret cannot accept one by forgetting to check the date itself.
     */
    @Test
    void anExpiredTokenIsNotUsableButIsStillFound() {
        userUtilService.setUserVcsAccessTokenAndExpiryDateAndSave(user, "token-value", ZonedDateTime.now().minusDays(1));

        assertThat(userVcsAccessTokenService.findToken(user.getId())).as("an expired token is still found, so that its owner can revoke it").isEqualTo("token-value");
        assertThat(userVcsAccessTokenService.findUsableToken(user.getId())).isEmpty();
    }

    /**
     * Revocation deletes the row rather than nulling the columns, so that "no row" stays the single representation of
     * "no token".
     */
    @Test
    void revokingRemovesTheRow() {
        userUtilService.setUserVcsAccessTokenAndExpiryDateAndSave(user, "token-value", ZonedDateTime.now().plusDays(1));

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
        userUtilService.setUserVcsAccessTokenAndExpiryDateAndSave(user, "inside", ZonedDateTime.now().plusDays(3));
        userUtilService.setUserVcsAccessTokenAndExpiryDateAndSave(other, "outside", ZonedDateTime.now().plusDays(90));

        var found = userVcsAccessTokenService.findUserIdsWithTokenExpiringBetween(ZonedDateTime.now(), ZonedDateTime.now().plusDays(7));

        assertThat(found).contains(user.getId()).doesNotContain(other.getId());
    }
}
