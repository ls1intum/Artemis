package de.tum.cit.aet.artemis.core.util;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;

import de.tum.cit.aet.artemis.core.security.jwt.TokenProvider;

/**
 * Lets a test act as a session that was established at a given moment, the way an authentication built from a signed token is.
 * <p>
 * A mock user carries no session start, and is therefore not subject to the check that keeps a session which survived a credential change
 * from creating credentials. This replaces the mock user's authentication by one that carries the start the filter would have read from
 * the token's {@code iat}, and keeps the authorities the mock user had, so that role checks behave as before.
 */
public final class SessionTestUtil {

    private SessionTestUtil() {
    }

    /**
     * Makes the following requests of the test come from a session that was established at the given moment.
     *
     * @param login            the account the session belongs to
     * @param sessionStartedAt when the session was established
     */
    public static void actAsSessionStartedAt(String login, Instant sessionStartedAt) {
        var current = TestSecurityContextHolder.getContext().getAuthentication();
        Collection<? extends GrantedAuthority> authorities = current != null ? current.getAuthorities() : List.of(new SimpleGrantedAuthority("ROLE_USER"));
        var authentication = new UsernamePasswordAuthenticationToken(login, "token", authorities);
        authentication.setDetails(Map.of(TokenProvider.SESSION_STARTED_AT, sessionStartedAt));
        TestSecurityContextHolder.setAuthentication(authentication);
        SecurityContextHolder.setContext(TestSecurityContextHolder.getContext());
    }
}
