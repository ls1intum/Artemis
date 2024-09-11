package de.tum.cit.aet.artemis.lti.config;

import java.net.URI;
import java.security.KeyPair;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import de.tum.cit.aet.artemis.core.security.OAuth2JWKSService;

/**
 * This class is responsible to retrieve access tokens from an LTI 1.3 platform of a specific ClientRegistration.
 */
@Component
@Profile("lti")
public class Lti13TokenRetriever {

    private final OAuth2JWKSService oAuth2JWKSService;

    private final RestTemplate restTemplate;

    private static final Logger log = LoggerFactory.getLogger(Lti13TokenRetriever.class);

    public static final int JWT_LIFETIME_SECONDS = 60;

    public Lti13TokenRetriever(OAuth2JWKSService keyPairService, RestTemplate restTemplate) {
        this.oAuth2JWKSService = keyPairService;
        this.restTemplate = restTemplate;
    }

    /**
     * Returns an access token for a specific client to be used to authenticate further communication with the related
     * LTI 1.3 platform of that client. The queried access token (if there is any) will be valid for the requested scopes.
     *
     * @param clientRegistration to query a token for
     * @param scopes             to ask access for
     * @return the access token to be used to authenticate requests to the client's LTI 1.3 platform.
     */
    public String getToken(ClientRegistration clientRegistration, String... scopes) {
        log.info("Trying to retrieve access token for client");

        Objects.requireNonNull(clientRegistration, "You must supply a clientRegistration.");
        if (scopes.length == 0) {
            throw new IllegalArgumentException("You must supply some scopes to request.");
        }

        SignedJWT signedJWT = createJWT(clientRegistration);
        if (signedJWT == null) {
            return null;
        }

        MultiValueMap<String, String> formData = buildFormData(signedJWT, scopes);
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        try {
            URI url = URI.create(clientRegistration.getProviderDetails().getTokenUri());
            RequestEntity<MultiValueMap<String, String>> requestEntity = new RequestEntity<>(formData, headers, HttpMethod.POST, url);
            ResponseEntity<String> exchange = restTemplate.exchange(requestEntity, String.class);
            if (exchange.getBody() == null) {
                return null;
            }
            return new ObjectMapper().readTree(exchange.getBody()).get("access_token").asText();
        }
        catch (HttpClientErrorException | JsonProcessingException e) {
            log.error("Could not retrieve access token for client {}: {}", clientRegistration.getClientId(), e.getMessage());
            return null;
        }
    }

    /**
     * Creates a signed JWT for LTI Deep Linking using a specific client registration ID and a set of custom claims.
     * The JWT is signed using the RSA algorithm with SHA-256.
     *
     * @param clientRegistrationId The client registration ID associated with the JWK to be used for signing the JWT.
     * @param customClaims         A map of custom claims to be included in the JWT. These claims are additional data
     *                                 that the consuming service may require.
     * @return A serialized signed JWT as a String.
     * @throws IllegalArgumentException If no JWK could be retrieved for the provided client registration ID.
     */
    public String createDeepLinkingJWT(String clientRegistrationId, Map<String, Object> customClaims) {
        JWK jwk = oAuth2JWKSService.getJWK(clientRegistrationId);

        if (jwk == null) {
            throw new IllegalArgumentException("Failed to get JWK for client registration: " + clientRegistrationId);
        }

        try {
            KeyPair keyPair = jwk.toRSAKey().toKeyPair();
            RSASSASigner signer = new RSASSASigner(keyPair.getPrivate());

            var claimSetBuilder = new JWTClaimsSet.Builder();
            for (Map.Entry<String, Object> entry : customClaims.entrySet()) {
                claimSetBuilder.claim(entry.getKey(), entry.getValue());
            }

            var now = Instant.now();
            JWTClaimsSet claimsSet = claimSetBuilder.issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(JWT_LIFETIME_SECONDS))).build();

            JWSHeader jwt = new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).keyID(jwk.getKeyID()).build();
            SignedJWT signedJWT = new SignedJWT(jwt, claimsSet);
            signedJWT.sign(signer);

            log.debug("Created signed token: {}", signedJWT.serialize());
            return signedJWT.serialize();
        }
        catch (JOSEException e) {
            log.error("Could not create keypair for clientRegistrationId {}", clientRegistrationId);
            return null;
        }
    }

    private SignedJWT createJWT(ClientRegistration clientRegistration) {
        JWK jwk = oAuth2JWKSService.getJWK(clientRegistration.getRegistrationId());

        if (jwk == null) {
            throw new IllegalArgumentException("Failed to get JWK for client registration: " + clientRegistration.getRegistrationId());
        }

        try {
            KeyPair keyPair = jwk.toRSAKey().toKeyPair();
            RSASSASigner signer = new RSASSASigner(keyPair.getPrivate());

            var now = Instant.now();
            JWTClaimsSet claimsSet = new JWTClaimsSet.Builder().issuer(clientRegistration.getClientId()).subject(clientRegistration.getClientId())
                    .audience(clientRegistration.getProviderDetails().getTokenUri()).issueTime(Date.from(now)).jwtID(UUID.randomUUID().toString())
                    .expirationTime(Date.from(now.plusSeconds(JWT_LIFETIME_SECONDS))).build();

            JWSHeader jwt = new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).keyID(jwk.getKeyID()).build();
            SignedJWT signedJWT = new SignedJWT(jwt, claimsSet);
            signedJWT.sign(signer);

            log.debug("Created signed token: {}", signedJWT.serialize());
            return signedJWT;
        }
        catch (JOSEException e) {
            log.error("Could not create keypair for clientRegistrationId {}", clientRegistration.getRegistrationId());
            return null;
        }
    }

    private MultiValueMap<String, String> buildFormData(SignedJWT signedJWT, String[] scopes) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "client_credentials");
        formData.add("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer");
        formData.add("scope", String.join(" ", scopes));
        formData.add("client_assertion", signedJWT.serialize());
        return formData;
    }
}
