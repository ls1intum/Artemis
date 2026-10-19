package de.tum.cit.aet.artemis.core.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;

import com.nimbusds.jose.jwk.JWK;

import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;
import de.tum.cit.aet.artemis.lti.service.OAuth2JWKSInitialisationService;
import de.tum.cit.aet.artemis.lti.service.OAuth2JWKSService;
import de.tum.cit.aet.artemis.lti.service.OnlineCourseConfigurationService;

class OAuth2JWKSServiceTest {

    @Mock
    private OnlineCourseConfigurationService onlineCourseConfigurationService;

    private OAuth2JWKSService oAuth2JWKSService;

    private ClientRegistration clientRegistration;

    private final String clientRegistrationId = "regId";

    private AutoCloseable closeable;

    @BeforeEach
    void init() {
        closeable = MockitoAnnotations.openMocks(this);
        SecurityContextHolder.clearContext();

        clientRegistration = mock(ClientRegistration.class);
        when(clientRegistration.getRegistrationId()).thenReturn(clientRegistrationId);
        when(onlineCourseConfigurationService.getAllClientRegistrations()).thenReturn(List.of(clientRegistration));

        // Use the real local provider: this service relies on computeIfAbsent/isEmpty/remove semantics that a mocked
        // map cannot faithfully reproduce.

        oAuth2JWKSService = new OAuth2JWKSService(onlineCourseConfigurationService, new LocalDataProviderService());
        oAuth2JWKSService.getClientRegistrationIdToJwk().clear();
        OAuth2JWKSInitialisationService oAuth2JWKSInitialisationService = new OAuth2JWKSInitialisationService(oAuth2JWKSService, onlineCourseConfigurationService);
        oAuth2JWKSInitialisationService.init();  // Manually call the initialization method to populate the JWKs
    }

    @AfterEach
    void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
        reset(onlineCourseConfigurationService);
    }

    @Test
    void getJWK() {
        JWK mockJwk = mock(JWK.class);
        oAuth2JWKSService.getClientRegistrationIdToJwk().put(clientRegistrationId, mockJwk);

        JWK jwk = oAuth2JWKSService.getJWK(clientRegistrationId);
        assertThat(jwk).isSameAs(mockJwk);
    }

    @Test
    void updateKey() {
        when(onlineCourseConfigurationService.findByRegistrationId(clientRegistrationId)).thenReturn(clientRegistration);

        oAuth2JWKSService.updateKey(clientRegistrationId);

        assertThat(oAuth2JWKSService.getClientRegistrationIdToJwk().get(clientRegistrationId)).as("a key is generated and stored for the registration").isNotNull();
    }

    @Test
    void updateKeyMissingClientRegistration() {
        when(onlineCourseConfigurationService.findByRegistrationId(clientRegistrationId)).thenReturn(null);

        oAuth2JWKSService.updateKey(clientRegistrationId);

        assertThat(oAuth2JWKSService.getClientRegistrationIdToJwk().get(clientRegistrationId)).as("no key is stored when the registration is missing").isNull();
    }
}
