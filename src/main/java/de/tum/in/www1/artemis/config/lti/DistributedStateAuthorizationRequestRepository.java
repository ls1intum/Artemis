package de.tum.in.www1.artemis.config.lti;

import java.util.Map;
import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.util.Assert;

import com.hazelcast.core.HazelcastInstance;

/**
 * This is a copy of {@link uk.ac.ox.ctl.lti13.security.oauth2.client.lti.web.StateAuthorizationRequestRepository}
 * but using Hazelcast to share the state between instances.
 * This is required to support LTI with multiple Artemis nodes.
 */
class DistributedStateAuthorizationRequestRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final Logger log = LoggerFactory.getLogger(DistributedStateAuthorizationRequestRepository.class);

    private final HazelcastInstance hazelcastInstance;

    private Map<String, OAuth2AuthorizationRequest> store;

    /**
     * Should we limit the login to a single IP address.
     * This may cause problems when users are on mobile devices and subsequent requests don't use the same IP address.
     */
    private boolean limitIpAddress = true;

    DistributedStateAuthorizationRequestRepository(HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
    }

    void init() {
        this.store = hazelcastInstance.getMap("ltiStateAuthorizationRequestStore");
    }

    public void setLimitIpAddress(boolean limitIpAddress) {
        this.limitIpAddress = limitIpAddress;
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        Objects.requireNonNull(request, "request cannot be null");
        String stateParameter = request.getParameter("state");
        if (stateParameter == null) {
            return null;
        }
        OAuth2AuthorizationRequest oAuth2AuthorizationRequest = this.store.get(stateParameter);
        if (oAuth2AuthorizationRequest == null) {
            return null;
        }

        String initialIp = oAuth2AuthorizationRequest.getAttribute("remote_ip");
        if (initialIp != null) {
            String requestIp = request.getRemoteAddr();
            if (!initialIp.equals(request.getRemoteAddr())) {
                log.info("IP mismatch detected. Initial IP: {}, Request IP: {}.", initialIp, requestIp);
                if (this.limitIpAddress) {
                    return null;
                }
            }
        }

        return oAuth2AuthorizationRequest;
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest request, HttpServletResponse response) {
        Objects.requireNonNull(request, "request cannot be null");
        Objects.requireNonNull(response, "response cannot be null");
        if (authorizationRequest == null) {
            this.removeAuthorizationRequest(request, response);
        }
        else {
            String state = authorizationRequest.getState();
            Assert.hasText(state, "authorizationRequest.state cannot be empty");
            this.store.put(state, authorizationRequest);
        }
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request, HttpServletResponse response) {
        OAuth2AuthorizationRequest authorizationRequest = this.loadAuthorizationRequest(request);
        if (authorizationRequest != null) {
            String stateParameter = request.getParameter("state");
            this.store.remove(stateParameter);
        }

        return authorizationRequest;
    }
}
