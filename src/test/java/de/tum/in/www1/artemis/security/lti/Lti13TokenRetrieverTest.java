package de.tum.in.www1.artemis.security.lti;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.keygen.Base64StringKeyGenerator;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;

import de.tum.in.www1.artemis.domain.lti.Scopes;
import de.tum.in.www1.artemis.security.OAuth2JWKSService;

class Lti13TokenRetrieverTest {

    @Mock
    private OAuth2JWKSService oAuth2JWKSService;

    @Mock
    private RestTemplate restTemplate;

    private Lti13TokenRetriever lti13TokenRetriever;

    private ClientRegistration clientRegistration;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        SecurityContextHolder.clearContext();
        lti13TokenRetriever = new Lti13TokenRetriever(oAuth2JWKSService, restTemplate);

        clientRegistration = ClientRegistration.withRegistrationId("regId") //
                .authorizationGrantType(AuthorizationGrantType.IMPLICIT) //
                .redirectUri("redirectUri") //
                .authorizationUri("authUri") //
                .tokenUri("tokenUri").clientId("clientId") //
                .build();
    }

    @Test
    void getTokenNoScopes() {
        assertThrows(IllegalArgumentException.class, () -> lti13TokenRetriever.getToken(clientRegistration));

        verifyNoInteractions(restTemplate);
    }

    @Test
    void getTokenNoRegistrationId() {
        assertThrows(NullPointerException.class, () -> lti13TokenRetriever.getToken(null, Scopes.AGS_SCORE));

        verifyNoInteractions(restTemplate);
    }

    @Test
    void getTokenNoJWK() {
        when(oAuth2JWKSService.getJWK(any())).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> lti13TokenRetriever.getToken(clientRegistration, Scopes.AGS_SCORE));

        verifyNoInteractions(restTemplate);
    }

    @Test
    void getTokenInvalidKey() throws JOSEException {
        RSAKey jwk = mock(RSAKey.class);
        when(oAuth2JWKSService.getJWK(any())).thenReturn(jwk);
        KeyPair keyPair = mock(KeyPair.class);
        when(jwk.toRSAKey()).thenReturn(jwk);
        when(jwk.toKeyPair()).thenReturn(keyPair);
        PrivateKey key = mock(PrivateKey.class);
        when(keyPair.getPrivate()).thenReturn(key);
        when(key.getAlgorithm()).thenReturn("RSA");

        String token = lti13TokenRetriever.getToken(clientRegistration, Scopes.AGS_SCORE);

        assertNull(token);
        verifyNoInteractions(restTemplate);
    }

    @Test
    void getTokenClientError() throws NoSuchAlgorithmException {
        JWK jwk = generateKey();
        when(oAuth2JWKSService.getJWK(any())).thenReturn(jwk);

        doThrow(HttpClientErrorException.class).when(restTemplate).exchange(any(), eq(String.class));

        String token = lti13TokenRetriever.getToken(clientRegistration, Scopes.AGS_SCORE);

        verify(oAuth2JWKSService, times(1)).getJWK(clientRegistration.getRegistrationId());
        verify(restTemplate, times(1)).exchange(any(), eq(String.class));

        assertNull(token);
    }

    @Test
    void getTokenNoReturn() throws NoSuchAlgorithmException {
        JWK jwk = generateKey();
        when(oAuth2JWKSService.getJWK(any())).thenReturn(jwk);

        ResponseEntity<String> responseEntity = ResponseEntity.of(Optional.empty());
        when(restTemplate.exchange(any(), eq(String.class))).thenReturn(responseEntity);

        String token = lti13TokenRetriever.getToken(clientRegistration, Scopes.AGS_SCORE);

        verify(oAuth2JWKSService, times(1)).getJWK(clientRegistration.getRegistrationId());
        verify(restTemplate, times(1)).exchange(any(), eq(String.class));

        assertNull(token);
    }

    @Test
    void getToken() throws NoSuchAlgorithmException {
        JWK jwk = generateKey();
        when(oAuth2JWKSService.getJWK(any())).thenReturn(jwk);

        Map<String, String> map = new HashMap<>();
        map.put("access_token", "result");
        ResponseEntity<String> responseEntity = ResponseEntity.of(Optional.of(map.toString()));
        when(restTemplate.exchange(any(), eq(String.class))).thenReturn(responseEntity);

        String token = lti13TokenRetriever.getToken(clientRegistration, Scopes.AGS_SCORE);

        verify(oAuth2JWKSService, times(1)).getJWK(clientRegistration.getRegistrationId());
        verify(restTemplate, times(1)).exchange(any(), eq(String.class));

        assertEquals("result", token);
    }

    private JWK generateKey() throws NoSuchAlgorithmException {
        KeyPair clientKeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        StringKeyGenerator kidGenerator = new Base64StringKeyGenerator(32);
        String kid = kidGenerator.generateKey();
        RSAKey.Builder builder = new RSAKey.Builder((RSAPublicKey) clientKeyPair.getPublic()).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256)
                .privateKey(clientKeyPair.getPrivate()).keyID(kid);
        return builder.build();
    }

}
