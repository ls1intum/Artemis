package de.tum.in.www1.artemis.security.lti;

import java.net.URI;
import java.security.KeyPair;
import java.time.Instant;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.google.gson.JsonParser;
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
 * This class is responsible to retrieve access tokens from an LTI 1.3 platform of a specific ClientRegistration.
 *
 */
@Component
public class Lti13TokenRetriever {

    private final OAuth2JWKSService oAuth2JWKSService;

    private final RestTemplate restTemplate;

    private final Logger log = LoggerFactory.getLogger(Lti13TokenRetriever.class);

    private static final int JWT_LIFETIME = 60;

    public Lti13TokenRetriever(OAuth2JWKSService keyPairService) {
        this.oAuth2JWKSService = keyPairService;
        this.restTemplate = new RestTemplate();
    }

    /**
     * Returns an access token for a specific client to be used to authenticate further communication with the related
     * LTI 1.3 platform of that client. The queried access token (if there is any) will be valid for the requested scopes.
     *
     * @param clientRegistration to query a token for
     * @param scopes to ask access for
     * @return the access token to be used to authenticate requests to the client's LTI 1.3 platform.
     */
    public String getToken(ClientRegistration clientRegistration, String... scopes) {
        log.info("Trying to retrieve access token for client");
        try {
            if (scopes.length == 0) {
                throw new IllegalArgumentException("You must supply some scopes to request.");
            }
            Objects.requireNonNull(clientRegistration, "You must supply a clientRegistration.");

            SignedJWT signedJWT = createJWT(clientRegistration);
            MultiValueMap<String, String> formData = buildFormData(signedJWT, scopes);
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

            URI url = URI.create(clientRegistration.getProviderDetails().getTokenUri());
            RequestEntity<MultiValueMap<String, String>> requestEntity = new RequestEntity<>(formData, headers, HttpMethod.POST, url);
            ResponseEntity<String> exchange = restTemplate.exchange(requestEntity, String.class);
            if (exchange.getBody() == null) {
                return null;
            }
            return JsonParser.parseString(exchange.getBody()).getAsJsonObject().get("access_token").getAsString();
        }
        catch (Exception e) {
            log.error("Could not retrieve access token for client {}: {}", clientRegistration.getClientId(), e.getMessage());
            return null;
        }
    }

    private SignedJWT createJWT(ClientRegistration clientRegistration) throws JOSEException {
        JWK jwk = oAuth2JWKSService.getJWK(clientRegistration);

        if (jwk == null) {
            throw new NullPointerException("Failed to get JWK for client registration: " + clientRegistration.getRegistrationId());
        }

        KeyPair keyPair = jwk.toRSAKey().toKeyPair();

        RSASSASigner signer = new RSASSASigner(keyPair.getPrivate());

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder().issuer(clientRegistration.getClientId()).subject(clientRegistration.getClientId())
                .audience(clientRegistration.getProviderDetails().getTokenUri()).issueTime(Date.from(Instant.now())).jwtID(UUID.randomUUID().toString())
                .expirationTime(Date.from(Instant.now().plusSeconds(JWT_LIFETIME))).build();

        JWSHeader jwt = new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).keyID(jwk.getKeyID()).build();
        SignedJWT signedJWT = new SignedJWT(jwt, claimsSet);
        signedJWT.sign(signer);

        log.debug("Created signed token: {}", signedJWT.serialize());

        return signedJWT;
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
