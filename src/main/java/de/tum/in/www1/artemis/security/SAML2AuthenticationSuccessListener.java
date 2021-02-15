package de.tum.in.www1.artemis.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.security.saml2.provider.service.servlet.filter.Saml2WebSsoAuthenticationFilter;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.service.connectors.SAML2Service;


/**
 * This class describes a SAML2 authentication success listener.
 * 
 * On a successfull login via SAML2 onApplicationEvent() is called with Saml2WebSsoAuthenticationFilter.class as source.
 * The attrubtes are then forwarded to the SAML2 service which does register and/or login the user.
 * 
 * The Saml2AuthenticatedPrincipal carries the SAML2 attributes, those are used to extract the user information.
 */
@Component
@Profile("saml2")
public class SAML2AuthenticationSuccessListener implements ApplicationListener<InteractiveAuthenticationSuccessEvent> {

    private final Logger log = LoggerFactory.getLogger(SAML2AuthenticationSuccessListener.class);

    @Autowired
    private SAML2Service saml2Service;

    @Override
    public void onApplicationEvent(final InteractiveAuthenticationSuccessEvent event) {
        // only listen to events created by a SAML2 Login
        if (event.getGeneratedBy() != Saml2WebSsoAuthenticationFilter.class) {
            return;
        }
        if (event.getAuthentication().getPrincipal() instanceof Saml2AuthenticatedPrincipal) {
            Saml2AuthenticatedPrincipal principal = (Saml2AuthenticatedPrincipal) event.getAuthentication().getPrincipal();
            saml2Service.handleAuthentication(principal);
        }
    }
}
