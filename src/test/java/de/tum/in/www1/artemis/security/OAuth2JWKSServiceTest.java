package de.tum.in.www1.artemis.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.Collections;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;

import com.nimbusds.jose.jwk.JWK;

import de.tum.in.www1.artemis.service.OnlineCourseConfigurationService;

class OAuth2JWKSServiceTest {

    @Mock
    private OnlineCourseConfigurationService onlineCourseConfigurationService;

    private OAuth2JWKSService oAuth2JWKSService;

    private ClientRegistration clientRegistration;

    private String clientRegistrationId;

    private AutoCloseable closeable;

    @BeforeEach
    void init() {
        closeable = MockitoAnnotations.openMocks(this);
        SecurityContextHolder.clearContext();

        clientRegistrationId = "regId";

        clientRegistration = mock(ClientRegistration.class);
        when(clientRegistration.getRegistrationId()).thenReturn("regId");
        when(onlineCourseConfigurationService.getAllClientRegistrations()).thenReturn(Collections.singletonList(clientRegistration));

        oAuth2JWKSService = new OAuth2JWKSService(onlineCourseConfigurationService);
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
        JWK jwk = oAuth2JWKSService.getJWK(clientRegistrationId);
        assertThat(jwk).isNotNull();
    }

    @Test
    void updateKey() {
        when(onlineCourseConfigurationService.findByRegistrationId(clientRegistrationId)).thenReturn(clientRegistration);

        oAuth2JWKSService.updateKey("regId");

        assertThat(oAuth2JWKSService.getJwkSet().getKeys()).hasSize(1);
    }

    @Test
    void updateKeyMissingClientRegistration() {
        when(onlineCourseConfigurationService.findByRegistrationId(clientRegistrationId)).thenReturn(null);

        oAuth2JWKSService.updateKey("regId");

        assertThat(oAuth2JWKSService.getJwkSet().getKeys()).isEmpty();
    }
}
