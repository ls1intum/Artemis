package de.tum.in.www1.artemis.security.lti;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.*;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
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
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import de.tum.in.www1.artemis.domain.lti.Scopes;
import de.tum.in.www1.artemis.security.OAuth2JWKSService;

class Lti13TokenRetrieverTest {

    @Mock
    private OAuth2JWKSService oAuth2JWKSService;

    @Mock
    private RestTemplate restTemplate;

    private Lti13TokenRetriever lti13TokenRetriever;

    private ClientRegistration clientRegistration;

    private AutoCloseable closeable;

    @BeforeEach
    void init() {
        closeable = MockitoAnnotations.openMocks(this);
        SecurityContextHolder.clearContext();
        lti13TokenRetriever = new Lti13TokenRetriever(oAuth2JWKSService, restTemplate);

        clientRegistration = ClientRegistration.withRegistrationId("regId") //
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE) //
                .redirectUri("redirectUri") //
                .authorizationUri("authUri") //
                .tokenUri("tokenUri").clientId("clientId") //
                .build();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
        reset(oAuth2JWKSService, restTemplate);
    }

    @Test
    void getTokenNoScopes() {
        assertThatIllegalArgumentException().isThrownBy(() -> lti13TokenRetriever.getToken(clientRegistration));

        verifyNoInteractions(restTemplate);
    }

    @Test
    void getTokenNoRegistrationId() {
        assertThatNullPointerException().isThrownBy(() -> lti13TokenRetriever.getToken(null, Scopes.AGS_SCORE));

        verifyNoInteractions(restTemplate);
    }

    @Test
    void getTokenNoJWK() {
        when(oAuth2JWKSService.getJWK(any())).thenReturn(null);

        assertThatIllegalArgumentException().isThrownBy(() -> lti13TokenRetriever.getToken(clientRegistration, Scopes.AGS_SCORE));

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

        assertThat(token).isNull();
        verifyNoInteractions(restTemplate);
    }

    @Test
    void getTokenClientError() throws NoSuchAlgorithmException {
        JWK jwk = generateKey();
        when(oAuth2JWKSService.getJWK(any())).thenReturn(jwk);

        doThrow(HttpClientErrorException.class).when(restTemplate).exchange(any(), eq(String.class));

        String token = lti13TokenRetriever.getToken(clientRegistration, Scopes.AGS_SCORE);

        verify(oAuth2JWKSService).getJWK(clientRegistration.getRegistrationId());
        verify(restTemplate).exchange(any(), eq(String.class));

        assertThat(token).isNull();
    }

    @Test
    void getTokenNoReturn() throws NoSuchAlgorithmException {
        JWK jwk = generateKey();
        when(oAuth2JWKSService.getJWK(any())).thenReturn(jwk);

        ResponseEntity<String> responseEntity = ResponseEntity.of(Optional.empty());
        when(restTemplate.exchange(any(), eq(String.class))).thenReturn(responseEntity);

        String token = lti13TokenRetriever.getToken(clientRegistration, Scopes.AGS_SCORE);

        verify(oAuth2JWKSService).getJWK(clientRegistration.getRegistrationId());
        verify(restTemplate).exchange(any(), eq(String.class));

        assertThat(token).isNull();
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

        verify(oAuth2JWKSService).getJWK(clientRegistration.getRegistrationId());
        verify(restTemplate).exchange(any(), eq(String.class));

        assertThat(token).isEqualTo("result");
    }

    @Test
    void getJWTToken() throws NoSuchAlgorithmException, ParseException {
        JWK jwk = generateKey();
        when(oAuth2JWKSService.getJWK(any())).thenReturn(jwk);

        Map<String, Object> claims = new HashMap<>();
        claims.put("customClaim1", "value1");
        claims.put("customClaim2", "value2");

        String token = lti13TokenRetriever.createDeepLinkingJWT(clientRegistration.getRegistrationId(), claims);

        verify(oAuth2JWKSService).getJWK(clientRegistration.getRegistrationId());
        assertThat(token).isNotNull();

        SignedJWT signedJWT = SignedJWT.parse(token);
        JWTClaimsSet claimsSet = signedJWT.getJWTClaimsSet();

        for (Map.Entry<String, Object> entry : claims.entrySet()) {
            assertThat(entry.getValue()).isEqualTo(claimsSet.getClaim(entry.getKey()));
        }
        assertThat(JWSAlgorithm.RS256).isEqualTo(signedJWT.getHeader().getAlgorithm());
        assertThat(JOSEObjectType.JWT).isEqualTo(signedJWT.getHeader().getType());
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
