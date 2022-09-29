package de.tum.in.www1.artemis.security.lti;

import java.net.URI;
import java.security.KeyPair;
import java.time.Instant;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import de.tum.in.www1.artemis.security.OAuth2JWKSService;

/**
 * A LTI 1.3 access token retriever that is designed to work with OpenOLAT 16.0.0 (LTI 1.3 Beta).
 *
 * The implementation is mainly derived from lti13-spring-security TokenRetriever.
 * However, OpenOLAT currently (at v16.0.0) does not return an access token if there are multiple tool deployments
 * with the same client-id. In this case it expects the access token request parameter "iss" to be the targetLinkUri of
 * the resource link of one of those tool deployments. In contrast, the LTI 1.3 specification states that the parameters
 * "iss" and "sub" must both contain the client-id.
 *
 * Furthermore, OpenOLAT requires the jwt header parameter "kid" to be set in order to identify the key to use to verify
 * the token request jws. lti13-spring-security TokenRetriever intentionally does not set "kid" as this is a purely OPTIONAL
 * parameter (as of JWT specification).
 */
public class Lti13OLATFallbackTokenRetriever {

    private OAuth2JWKSService OAuth2JWKSService;

    private final RestTemplate restTemplate;

    // Lifetime of our JWT in seconds
    private int jwtLifetime = 60;

    private final Logger log = LoggerFactory.getLogger(Lti13OLATFallbackTokenRetriever.class);

    public Lti13OLATFallbackTokenRetriever(OAuth2JWKSService OAuth2JWKSService) {
        restTemplate = new RestTemplate(Arrays.asList(new FormHttpMessageConverter(), new OAuth2AccessTokenResponseHttpMessageConverter()));
        restTemplate.setErrorHandler(new OAuth2ErrorResponseErrorHandler());
        this.OAuth2JWKSService = OAuth2JWKSService;
    }

    public void setJwtLifetime(int jwtLifetime) {
        this.jwtLifetime = jwtLifetime;
    }

    public OAuth2AccessTokenResponse getToken(ClientRegistration clientRegistration, String targetLinkUri, String... scopes) throws JOSEException {
        if (scopes.length == 0) {
            throw new IllegalArgumentException("You must supply some scopes to request.");
        }
        Objects.requireNonNull(clientRegistration, "You must supply a clientRegistration.");

        SignedJWT signedJWT = createJWT(clientRegistration, targetLinkUri);
        MultiValueMap<String, String> formData = buildFormData(signedJWT, scopes);
        // We are using RestTemplate here as that's what the existing OAuth2 code in Spring uses at the moment.
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        RequestEntity<MultiValueMap<String, String>> requestEntity = new RequestEntity<>(formData, headers, HttpMethod.POST,
                URI.create(clientRegistration.getProviderDetails().getTokenUri()));
        ResponseEntity<OAuth2AccessTokenResponse> exchange = restTemplate.exchange(requestEntity, OAuth2AccessTokenResponse.class);
        return exchange.getBody();
    }

    public SignedJWT createJWT(ClientRegistration clientRegistration, String targetLinkUri) throws JOSEException {
        // In contrast to lti13-spring-security we cannot solely rely on the KeyPair
        // because one looses the "kid" if a JWK is converted to a KeyPair.
        JWK jwk = OAuth2JWKSService.getJWK(clientRegistration);

        if (jwk == null) {
            throw new NullPointerException("Failed to get JWK for client registration: " + clientRegistration.getRegistrationId());
        }

        KeyPair keyPair = jwk.toRSAKey().toKeyPair();

        RSASSASigner signer = new RSASSASigner(keyPair.getPrivate());

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                // Both must be set to client ID according to LTI 1.3 specification.
                // But OpenOLAT currently fails to handle this case if multiple tool deployments
                // with the same client-id are present - in this case OpenOLAT expects the issuer to be
                // the targetLinkUri of that tool deployment.
                .issuer(targetLinkUri).subject(clientRegistration.getClientId()).audience(clientRegistration.getProviderDetails().getTokenUri()).issueTime(Date.from(Instant.now()))
                .jwtID(UUID.randomUUID().toString())
                // 60 Seconds
                .expirationTime(Date.from(Instant.now().plusSeconds(jwtLifetime))).build();

        // Because of OpenOLAT, we do have to include a key ID although it is OPTIONAL according to JWT specification.
        JWSHeader jwt = new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).keyID(jwk.getKeyID()).build();
        SignedJWT signedJWT = new SignedJWT(jwt, claimsSet);
        signedJWT.sign(signer);

        if (log.isDebugEnabled()) {
            log.debug("Created signed token: {}", signedJWT.serialize());
        }

        return signedJWT;
    }

    public MultiValueMap<String, String> buildFormData(SignedJWT signedJWT, String[] scopes) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "client_credentials");
        formData.add("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer");
        formData.add("scope", String.join(" ", scopes));
        formData.add("client_assertion", signedJWT.serialize());
        return formData;
    }
}
