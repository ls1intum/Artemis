package de.tum.cit.aet.artemis.core.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.nimbusds.jose.jwk.JWK;

import de.tum.cit.aet.artemis.lti.service.OAuth2JWKSInitialisationService;
import de.tum.cit.aet.artemis.lti.service.OAuth2JWKSService;
import de.tum.cit.aet.artemis.lti.service.OnlineCourseConfigurationService;

class OAuth2JWKSServiceTest {

    @Mock
    private OnlineCourseConfigurationService onlineCourseConfigurationService;

    @Mock
    private HazelcastInstance hazelcastInstance;

    @Mock
    private IMap<String, JWK> clientRegistrationIdToJwk;

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
        when(onlineCourseConfigurationService.getAllClientRegistrations()).thenReturn(Collections.singletonList(clientRegistration));

        // Mock Hazelcast maps and behavior
        when(hazelcastInstance.getMap("ltiJwkMap")).thenAnswer(invocation -> clientRegistrationIdToJwk);

        oAuth2JWKSService = new OAuth2JWKSService(onlineCourseConfigurationService, hazelcastInstance);
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

        when(clientRegistrationIdToJwk.get(clientRegistrationId)).thenReturn(mockJwk);

        JWK jwk = oAuth2JWKSService.getJWK(clientRegistrationId);
        assertThat(jwk).isNotNull();
    }

    @Test
    void updateKey() {
        when(onlineCourseConfigurationService.findByRegistrationId(clientRegistrationId)).thenReturn(clientRegistration);

        oAuth2JWKSService.updateKey(clientRegistrationId);

        verify(clientRegistrationIdToJwk, atLeastOnce()).put(eq(clientRegistrationId), any(JWK.class));
    }

    @Test
    void updateKeyMissingClientRegistration() {
        when(onlineCourseConfigurationService.findByRegistrationId(clientRegistrationId)).thenReturn(null);

        oAuth2JWKSService.updateKey(clientRegistrationId);

        verify(clientRegistrationIdToJwk, never()).put(any(), any());
    }
}
