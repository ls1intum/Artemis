package de.tum.cit.aet.artemis.videosource.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import de.tum.cit.aet.artemis.videosource.config.GocastEnabled;

@Service
@Lazy
@Conditional(GocastEnabled.class)
public class GocastAuthenticationService {

    private static final long MAX_REFRESH_SKEW_SECONDS = 30;

    private static final long MAX_UNSIGNED_INT = 4_294_967_295L;

    private final RestClient restClient;

    private final String email;

    private final String password;

    private final Clock clock;

    private volatile CachedSession cachedSession;

    @Autowired
    public GocastAuthenticationService(@Qualifier("gocastIntegrationRestClient") RestClient restClient, @Value("${artemis.tum-live.service-account-email}") String email,
            @Value("${artemis.tum-live.service-account-password}") String password) {
        this(restClient, email, password, Clock.systemUTC());
    }

    GocastAuthenticationService(RestClient restClient, String email, String password, Clock clock) {
        this.restClient = restClient;
        this.email = email;
        this.password = password;
        this.clock = clock;
    }

    /**
     * Returns the current service-account session, logging in once when refresh is needed.
     *
     * @return a usable authenticated session
     */
    public Session getSession() {
        CachedSession current = cachedSession;
        if (isUsable(current)) {
            return current.session();
        }
        synchronized (this) {
            current = cachedSession;
            if (!isUsable(current)) {
                current = login();
                cachedSession = current;
            }
            return current.session();
        }
    }

    public synchronized void invalidate(String authorizationHeader) {
        if (cachedSession != null && cachedSession.session().authorizationHeader().equals(authorizationHeader)) {
            cachedSession = null;
        }
    }

    private boolean isUsable(CachedSession candidate) {
        return candidate != null && clock.instant().isBefore(candidate.refreshAt());
    }

    private CachedSession login() {
        try {
            LoginResponse response = restClient.post().uri("/integration/login").body(new LoginRequest(email, password)).retrieve().body(LoginResponse.class);
            if (response == null || !StringUtils.hasText(response.accessToken()) || !"Bearer".equals(response.tokenType()) || response.expiresIn() <= 0 || response.userId() <= 0
                    || response.userId() > MAX_UNSIGNED_INT) {
                throw new GocastIntegrationException("TUM.Live login returned an invalid response", HttpStatus.BAD_GATEWAY);
            }
            Instant now = clock.instant();
            long skew = Math.min(MAX_REFRESH_SKEW_SECONDS, response.expiresIn() / 10L);
            return new CachedSession(new Session("Bearer " + response.accessToken(), response.userId()), now.plusSeconds(response.expiresIn() - skew));
        }
        catch (GocastIntegrationException exception) {
            throw exception;
        }
        catch (RestClientException exception) {
            throw translate("TUM.Live login failed", exception);
        }
    }

    private static GocastIntegrationException translate(String message, RestClientException exception) {
        if (exception instanceof RestClientResponseException responseException) {
            return new GocastIntegrationException(message, responseException.getStatusCode(), exception);
        }
        return new GocastIntegrationException(message, exception instanceof ResourceAccessException ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY, exception);
    }

    public record Session(String authorizationHeader, long userId) {

        @Override
        public String toString() {
            return "Session[authorizationHeader=[REDACTED], userId=" + userId + "]";
        }
    }

    private record CachedSession(Session session, Instant refreshAt) {
    }

    private record LoginRequest(String email, String password) {

        @Override
        public String toString() {
            return "LoginRequest[email=" + email + ", password=[REDACTED]]";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LoginResponse(String accessToken, String tokenType, long expiresIn, long userId) {

        @Override
        public String toString() {
            return "LoginResponse[accessToken=[REDACTED], tokenType=" + tokenType + ", expiresIn=" + expiresIn + ", userId=" + userId + "]";
        }
    }
}
