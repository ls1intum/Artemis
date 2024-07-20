package de.tum.in.www1.artemis.config.lti;

import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.util.Assert;

import com.hazelcast.core.HazelcastInstance;

// TODO: Move this into the fork?
public class DistributedStateAuthorizationRequestRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    public static final String REMOTE_IP = "remote_ip";

    private final Map<String, OAuth2AuthorizationRequest> store;

    private boolean limitIpAddress = true;

    private BiConsumer<String, String> ipMismatchHandler = (a, b) -> {
    };

    public DistributedStateAuthorizationRequestRepository(HazelcastInstance hazelcastInstance) {
        this.store = hazelcastInstance.getMap("ltiStateAuthorizationRequestStore");
    }

    public void setLimitIpAddress(boolean limitIpAddress) {
        this.limitIpAddress = limitIpAddress;
    }

    public void setIpMismatchHandler(BiConsumer<String, String> ipMismatchHandler) {
        this.ipMismatchHandler = ipMismatchHandler;
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        Objects.requireNonNull(request, "request cannot be null");
        String stateParameter = request.getParameter("state");
        if (stateParameter == null) {
            return null;
        }
        else {
            OAuth2AuthorizationRequest oAuth2AuthorizationRequest = this.store.get(stateParameter);
            if (oAuth2AuthorizationRequest != null) {
                String initialIp = oAuth2AuthorizationRequest.getAttribute("remote_ip");
                if (initialIp != null) {
                    String requestIp = request.getRemoteAddr();
                    if (!initialIp.equals(request.getRemoteAddr())) {
                        this.ipMismatchHandler.accept(initialIp, requestIp);
                        if (this.limitIpAddress) {
                            return null;
                        }
                    }
                }
            }

            return oAuth2AuthorizationRequest;
        }
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest request, HttpServletResponse response) {
        Assert.notNull(request, "request cannot be null");
        Assert.notNull(response, "response cannot be null");
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
