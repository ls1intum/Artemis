package de.tum.in.www1.artemis.connector.saml2;

import de.tum.in.www1.artemis.config.SAML2Configuration;
import de.tum.in.www1.artemis.config.SAML2Properties;
import de.tum.in.www1.artemis.service.connectors.SAML2Service;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.stereotype.Component;

import static org.mockito.Mockito.doReturn;

@Component
@Profile("saml2")
public class Saml2MockProvider {

    @SpyBean
    @InjectMocks
    RelyingPartyRegistrationRepository relyingPartyRegistrationRepository;

    @SpyBean
    @InjectMocks
    private SAML2Properties saml2Properties;

    @SpyBean
    @InjectMocks
    private SAML2Service saml2Service;

    public Saml2MockProvider() {
    }

    public void mockHandleAuthentication(Authentication auth, Saml2AuthenticatedPrincipal principal) {
        doReturn(auth).when(saml2Service).handleAuthentication(principal);
    }

    public void resetSpyBeans() {
        Mockito.reset(saml2Properties, saml2Service);
    }
}
