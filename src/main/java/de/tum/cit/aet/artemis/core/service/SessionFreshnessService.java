package de.tum.cit.aet.artemis.core.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.SessionOutdatedException;
import de.tum.cit.aet.artemis.core.security.jwt.TokenProvider;

/**
 * Keeps a session that outlived a credential change from planting new access.
 * <p>
 * A password reset, a password change, a deactivation and an administrator's password update all stamp the time the
 * account's credentials changed. That time only ever reached the silent rotation of a session, so the session that was in
 * use during an intrusion kept working until its token expired, up to 24 hours for a normal login, 30 days for "remember
 * me" and 180 days for a passkey. In that window it could add a passkey, an SSH key or a VCS access token: each is a
 * permanent credential that survives the very password reset meant to end the intrusion.
 * <p>
 * This service closes that gap where it matters, at the moment a credential is created. Every operation that creates one
 * calls {@link #requireCurrentSession()} first, which compares the time the session was established, the {@code iat} of the
 * token, to the time the credentials last changed. The check costs two lookups, and only on those rare operations: nothing
 * is added to ordinary requests, which is why it can live here and not in the request filter.
 * <p>
 * It deliberately leaves everything else alone. A stale token can still read what the account may read until it expires.
 * Ending that earlier would need a lookup, or a cache of revocations, on every authenticated request.
 * <p>
 * A request whose authentication does not come from a signed token, for instance an internal call or a test with a mock
 * user, carries no session start and is not checked: there is no session that could predate the change. Every token this
 * server issues carries {@code iat}, and a token cannot be forged without the signing key.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class SessionFreshnessService {

    private static final Logger log = LoggerFactory.getLogger(SessionFreshnessService.class);

    private final PasskeyTokenRenewalService passkeyTokenRenewalService;

    public SessionFreshnessService(PasskeyTokenRenewalService passkeyTokenRenewalService) {
        this.passkeyTokenRenewalService = passkeyTokenRenewalService;
    }

    /**
     * Rejects the current request if its session was established before the credentials of its account last changed, or if the
     * account has been deactivated or deleted since.
     *
     * @throws SessionOutdatedException if the session is no longer allowed to create credentials
     */
    public void requireCurrentSession() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!isCurrentSession(authentication)) {
            log.info("Rejected a credential creation: the session of user {} predates the last credentials change", authentication == null ? null : authentication.getName());
            throw new SessionOutdatedException();
        }
    }

    /**
     * Whether the session behind an authentication may still create credentials.
     * <p>
     * Used directly where an exception cannot be thrown to the caller, such as the servlet filters of the passkey
     * registration, which have to write the response themselves.
     *
     * @param authentication the authentication of the request, may be {@code null}
     * @return {@code true} if the session may create credentials, or if the authentication carries no session start to check
     */
    public boolean isCurrentSession(Authentication authentication) {
        Optional<Instant> sessionStartedAt = sessionStartedAt(authentication);
        if (sessionStartedAt.isEmpty()) {
            return true;
        }
        return passkeyTokenRenewalService.mayExtendSessionForAccount(authentication.getName(), sessionStartedAt.get());
    }

    /**
     * Reads the original session start attached by token authentication. Silent token renewal preserves this instant.
     * Authentication without this metadata follows the documented internal/mock-user path in {@link #isCurrentSession}.
     *
     * @param authentication the request authentication, possibly {@code null}
     * @return the original session start when carried by the authentication
     */
    private static Optional<Instant> sessionStartedAt(Authentication authentication) {
        if (authentication != null && authentication.getDetails() instanceof Map<?, ?> details && details.get(TokenProvider.SESSION_STARTED_AT) instanceof Instant startedAt) {
            return Optional.of(startedAt);
        }
        return Optional.empty();
    }
}
