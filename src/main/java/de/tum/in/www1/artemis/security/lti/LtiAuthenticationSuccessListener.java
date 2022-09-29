package de.tum.in.www1.artemis.security.lti;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.service.connectors.LtiService;

/**
 * Created by Josias Montag on 22/11/2016.
 */
@Component
@ComponentScan("de.tum.in.www1.artemis.service.LtiService")
public class LtiAuthenticationSuccessListener implements ApplicationListener<InteractiveAuthenticationSuccessEvent> {

    private final Logger log = LoggerFactory.getLogger(LtiAuthenticationSuccessListener.class);

    private final LtiService ltiService;

    public LtiAuthenticationSuccessListener(LtiService ltiService) {
        this.ltiService = ltiService;
    }

    @Override
    public void onApplicationEvent(InteractiveAuthenticationSuccessEvent event) {
        // The InteractiveAuthenticationSuccessEvent is fired on manual logins and remember-me logins.
        // Not fired on programmatic logins!
        AbstractAuthenticationToken token = (AbstractAuthenticationToken) event.getSource();
        WebAuthenticationDetails authDetails = (WebAuthenticationDetails) token.getDetails();
        if (authDetails == null) {
            return;
        }
        String sessionId = authDetails.getSessionId();
        ltiService.handleLaunchRequestForSession(sessionId);
    }
}
