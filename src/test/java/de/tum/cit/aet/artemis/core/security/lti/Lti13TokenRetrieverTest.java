package de.tum.cit.aet.artemis.core.security.lti;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.keygen.Base64StringKeyGenerator;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import tools.jackson.core.JacksonException;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.lti.config.Lti13TokenRetriever;
import de.tum.cit.aet.artemis.lti.dto.Scopes;
import de.tum.cit.aet.artemis.lti.service.OAuth2JWKSService;
import uk.ac.ox.ctl.lti13.security.oauth2.client.lti.web.LTIAuthorizationGrantType;

class Lti13TokenRetrieverTest {

    @Mock
    private OAuth2JWKSService oAuth2JWKSService;

    private static final String TOKEN_URI = "https://lms.example/token";

    private MockRestServiceServer server;

    private Lti13TokenRetriever lti13TokenRetriever;

    private ClientRegistration clientRegistration;

    private AutoCloseable closeable;

    @BeforeEach
    void init() {
        closeable = MockitoAnnotations.openMocks(this);
        SecurityContextHolder.clearContext();
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        lti13TokenRetriever = new Lti13TokenRetriever(oAuth2JWKSService, builder.build());

        clientRegistration = ClientRegistration.withRegistrationId("regId") //
                .authorizationGrantType(LTIAuthorizationGrantType.IMPLICIT) //
                .redirectUri("redirectUri") //
                .authorizationUri("authUri") //
                .tokenUri(TOKEN_URI).clientId("clientId") //
                .build();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
        reset(oAuth2JWKSService);
    }

    @Test
    void getTokenNoScopes() {
        assertThatIllegalArgumentException().isThrownBy(() -> lti13TokenRetriever.getToken(clientRegistration));

        server.verify();
    }

    @Test
    void getTokenNoRegistrationId() {
        assertThatNullPointerException().isThrownBy(() -> lti13TokenRetriever.getToken(null, Scopes.AGS_SCORE));

        server.verify();
    }

    @Test
    void getTokenNoJWK() {
        when(oAuth2JWKSService.getJWK(any())).thenReturn(null);

        assertThatIllegalArgumentException().isThrownBy(() -> lti13TokenRetriever.getToken(clientRegistration, Scopes.AGS_SCORE));

        server.verify();
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
        server.verify();
    }

    @Test
    void getTokenClientError() throws NoSuchAlgorithmException {
        JWK jwk = generateKey();
        when(oAuth2JWKSService.getJWK(any())).thenReturn(jwk);

        server.expect(requestTo(TOKEN_URI)).andRespond(withBadRequest());

        String token = lti13TokenRetriever.getToken(clientRegistration, Scopes.AGS_SCORE);

        verify(oAuth2JWKSService).getJWK(clientRegistration.getRegistrationId());
        server.verify();

        assertThat(token).isNull();
    }

    @Test
    void getTokenNoReturn() throws NoSuchAlgorithmException {
        JWK jwk = generateKey();
        when(oAuth2JWKSService.getJWK(any())).thenReturn(jwk);

        server.expect(requestTo(TOKEN_URI)).andRespond(withSuccess());

        String token = lti13TokenRetriever.getToken(clientRegistration, Scopes.AGS_SCORE);

        verify(oAuth2JWKSService).getJWK(clientRegistration.getRegistrationId());
        server.verify();

        assertThat(token).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = { "{}", "{\"access_token\": null}", "{\"access_token\": \"\"}", "{\"access_token\": \"  \"}", "{\"access_token\": {\"nested\": 1}}", "[]",
            "\"access_token\"", "null", "", "not json" })
    void getTokenWithoutUsableAccessToken(String body) throws NoSuchAlgorithmException {
        JWK jwk = generateKey();
        when(oAuth2JWKSService.getJWK(any())).thenReturn(jwk);
        server.expect(requestTo(TOKEN_URI)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        String token = lti13TokenRetriever.getToken(clientRegistration, Scopes.AGS_SCORE);

        assertThat(token).isNull();
    }

    @Test
    void getTokenIgnoresTheOtherFieldsOfTheTokenResponse() throws NoSuchAlgorithmException {
        JWK jwk = generateKey();
        when(oAuth2JWKSService.getJWK(any())).thenReturn(jwk);
        String body = "{\"token_type\": \"bearer\", \"expires_in\": 3600, \"scope\": \"a b\", \"access_token\": \"result\"}";
        server.expect(requestTo(TOKEN_URI)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        String token = lti13TokenRetriever.getToken(clientRegistration, Scopes.AGS_SCORE);

        assertThat(token).isEqualTo("result");
    }

    @Test
    void getToken() throws NoSuchAlgorithmException, JacksonException {
        JWK jwk = generateKey();
        when(oAuth2JWKSService.getJWK(any())).thenReturn(jwk);

        Map<String, String> map = new HashMap<>();
        map.put("access_token", "result");
        // The platform gets a form-encoded POST that asks for JSON.
        server.expect(requestTo(TOKEN_URI)).andExpect(method(HttpMethod.POST)).andExpect(header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE))
                .andExpect(header("Accept", MediaType.APPLICATION_JSON_VALUE)).andRespond(withSuccess(JsonObjectMapper.get().writeValueAsString(map), MediaType.APPLICATION_JSON));

        String token = lti13TokenRetriever.getToken(clientRegistration, Scopes.AGS_SCORE);

        verify(oAuth2JWKSService).getJWK(clientRegistration.getRegistrationId());
        server.verify();

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
