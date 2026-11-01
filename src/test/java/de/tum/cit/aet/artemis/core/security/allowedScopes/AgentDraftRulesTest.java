package de.tum.cit.aet.artemis.core.security.allowedScopes;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZonedDateTime;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;

class AgentDraftRulesTest {

    private static final ZonedDateTime FUTURE = ZonedDateTime.now().plusDays(10);

    private static final ZonedDateTime LATER = ZonedDateTime.now().plusDays(20);

    private static final ZonedDateTime PAST = ZonedDateTime.now().minusDays(1);

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateWithToken() {
        SecurityContextHolder.getContext().setAuthentication(new PersonalAccessTokenAuthentication("instructor", Set.of(new SimpleGrantedAuthority("ROLE_USER")), 1L, Set.of()));
    }

    private static void authenticateWithSession() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("instructor", "n/a", Set.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @Test
    void aSessionRequestIsNeverRestricted() {
        authenticateWithSession();

        assertThatCode(() -> AgentDraftRules.requireDraftDatesForCreate(null, null)).doesNotThrowAnyException();
        assertThatCode(() -> AgentDraftRules.requireDraftDatesForCreate(PAST, null)).doesNotThrowAnyException();
        assertThatCode(() -> AgentDraftRules.requireStillDraft(PAST, null, null, null)).doesNotThrowAnyException();
    }

    @Test
    void aRequestWithoutAuthenticationIsNotATokenRequest() {
        assertThatCode(() -> AgentDraftRules.requireDraftDatesForCreate(null, null)).doesNotThrowAnyException();
    }

    @Test
    void aTokenMustCreateContentWithAFutureReleaseDate() {
        authenticateWithToken();

        assertThatCode(() -> AgentDraftRules.requireDraftDatesForCreate(FUTURE, null)).doesNotThrowAnyException();
        assertThatCode(() -> AgentDraftRules.requireDraftDatesForCreate(FUTURE, LATER)).doesNotThrowAnyException();
        assertThatThrownBy(() -> AgentDraftRules.requireDraftDatesForCreate(null, null)).isInstanceOf(BadRequestAlertException.class);
        assertThatThrownBy(() -> AgentDraftRules.requireDraftDatesForCreate(PAST, null)).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void aStartDateInThePastWouldPublishTheContent() {
        authenticateWithToken();

        assertThatThrownBy(() -> AgentDraftRules.requireDraftDatesForCreate(FUTURE, PAST)).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void aTokenCannotChangePublishedContent() {
        authenticateWithToken();

        assertThatThrownBy(() -> AgentDraftRules.requireStillDraft(PAST, null, FUTURE, null)).isInstanceOf(AccessForbiddenException.class);
        assertThatThrownBy(() -> AgentDraftRules.requireStillDraft(null, null, FUTURE, null)).isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    void aTokenCannotPublishDraftContentByChangingItsDates() {
        authenticateWithToken();

        assertThatCode(() -> AgentDraftRules.requireStillDraft(FUTURE, null, LATER, null)).doesNotThrowAnyException();
        assertThatThrownBy(() -> AgentDraftRules.requireStillDraft(FUTURE, null, null, null)).isInstanceOf(BadRequestAlertException.class);
        assertThatThrownBy(() -> AgentDraftRules.requireStillDraft(FUTURE, null, PAST, null)).isInstanceOf(BadRequestAlertException.class);
    }
}
