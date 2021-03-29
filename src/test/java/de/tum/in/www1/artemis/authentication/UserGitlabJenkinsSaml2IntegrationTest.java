package de.tum.in.www1.artemis.authentication;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import java.util.HashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.saml2.provider.service.authentication.DefaultSaml2AuthenticatedPrincipal;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.security.saml2.provider.service.authentication.Saml2Authentication;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.test.context.TestSecurityContextHolder;

import de.tum.in.www1.artemis.AbstractSpringIntegrationJenkinsGitlabSaml2Test;
import de.tum.in.www1.artemis.security.jwt.TokenProvider;
import de.tum.in.www1.artemis.service.connectors.SAML2Service;
import de.tum.in.www1.artemis.util.UserTestService;
import de.tum.in.www1.artemis.web.rest.UserJWTController;

public class UserGitlabJenkinsSaml2IntegrationTest extends AbstractSpringIntegrationJenkinsGitlabSaml2Test {

    @Autowired
    UserTestService userTestService;

    @Autowired
    SAML2Service saml2Service;

    @Autowired
    UserJWTController userJWTController;

    @Autowired
    TokenProvider tokenProvider;

    @MockBean
    RelyingPartyRegistrationRepository relyingPartyRegistrationRepository;

    @BeforeEach
    public void setUp() throws Exception {
        doReturn(null).when(relyingPartyRegistrationRepository).findByRegistrationId(anyString());
    }

    @Test
    public void testValidSaml2Login() {
        // Mock existing SAML2 Auth
        Saml2AuthenticatedPrincipal principal = new DefaultSaml2AuthenticatedPrincipal("user1", new HashMap<>());
        Authentication authentication = new Saml2Authentication(principal, "Secret Credentials", null);
        TestSecurityContextHolder.setAuthentication(authentication);
        saml2MockProvider.mockHandleAuthentication(authentication, principal);

        // Test whether authorizeSAML2 generates a valid token
        // TODO invoke the REST call using the requestUtil
        ResponseEntity<UserJWTController.JWTToken> result = userJWTController.authorizeSAML2("false");
        assertThat(this.tokenProvider.validateToken(result.getBody().getIdToken())).as("JWT Token is Valid").isTrue();

    }

}
