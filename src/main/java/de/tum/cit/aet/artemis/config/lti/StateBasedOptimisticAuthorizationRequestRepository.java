package de.tum.cit.aet.artemis.config.lti;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import uk.ac.ox.ctl.lti13.security.oauth2.client.lti.web.OptimisticAuthorizationRequestRepository;

/**
 * Custom repository for handling OAuth2 authorization requests in a state-based manner.
 * This implementation prioritizes state parameters over session-based storage for
 * managing authorization requests.
 */
public class StateBasedOptimisticAuthorizationRequestRepository extends OptimisticAuthorizationRequestRepository {

    private final AuthorizationRequestRepository<OAuth2AuthorizationRequest> stateBased;

    /**
     * Constructs a StateBasedOptimisticAuthorizationRequestRepository with specified
     * state-based and session-based repositories.
     *
     * @param sessionBased the session-based repository
     * @param stateBased   the state-based repository
     */
    public StateBasedOptimisticAuthorizationRequestRepository(AuthorizationRequestRepository<OAuth2AuthorizationRequest> sessionBased,
            AuthorizationRequestRepository<OAuth2AuthorizationRequest> stateBased) {
        super(sessionBased, stateBased);
        this.stateBased = stateBased;
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest request, HttpServletResponse response) {
        // Overriding only the saveAuthorizationRequest to enforce state-based handling.
        // This method is critical for initially capturing and storing the OAuth2 authorization request,
        // ensuring it uses state-based logic rather than session-based.
        stateBased.saveAuthorizationRequest(authorizationRequest, request, response);
    }
}
