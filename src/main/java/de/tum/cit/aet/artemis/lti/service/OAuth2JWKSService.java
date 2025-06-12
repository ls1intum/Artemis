package de.tum.cit.aet.artemis.lti.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LTI;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.keygen.Base64StringKeyGenerator;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;

/**
 * This Service is responsible to manage JWKs for all OAuth2 ClientRegistrations.
 * On initialisation, each ClientRegistration gets assigned a fresh generated RSAKey.
 */
@Service
@Profile(PROFILE_LTI)
public class OAuth2JWKSService {

    private final OnlineCourseConfigurationService onlineCourseConfigurationService;

    private final HazelcastInstance hazelcastInstance;

    private final StringKeyGenerator kidGenerator = new Base64StringKeyGenerator(32);

    private static final Logger log = LoggerFactory.getLogger(OAuth2JWKSService.class);

    private IMap<String, String> clientRegistrationIdToKeyId;

    private IMap<String, JWK> kidToJwk;

    public OAuth2JWKSService(OnlineCourseConfigurationService onlineCourseConfigurationService, HazelcastInstance hazelcastInstance) {
        this.onlineCourseConfigurationService = onlineCourseConfigurationService;
        this.hazelcastInstance = hazelcastInstance;
    }

    @PostConstruct
    public void init() {
        clientRegistrationIdToKeyId = hazelcastInstance.getMap("ltiClientRegistrationIdToKeyId");
        kidToJwk = hazelcastInstance.getMap("ltiJwkSet");

        // Only one node should initialize the JWKSet
        if (clientRegistrationIdToKeyId.isEmpty() && kidToJwk.isEmpty()) {
            log.debug("Initializing JWKSet for OAuth2 ClientRegistrations");
            generateOAuth2ClientKeys();
        }
    }

    /**
     * Returns the JWK created for a clientRegistrationId
     *
     * @param clientRegistrationId the client registrationId
     * @return the JWK found for the client registrationId
     */
    public JWK getJWK(String clientRegistrationId) {
        String kid = clientRegistrationIdToKeyId.get(clientRegistrationId);
        return (kid != null) ? kidToJwk.get(kid) : null;
    }

    /**
     * Updates the JWK associated with the clientRegistrationId receives. Can either remove, replace, or add a new one.
     *
     * @param clientRegistrationId the client registrationId
     */
    public void updateKey(String clientRegistrationId) {
        ClientRegistration clientRegistration = onlineCourseConfigurationService.findByRegistrationId(clientRegistrationId);
        String oldKid = clientRegistrationIdToKeyId.get(clientRegistrationId);

        if (clientRegistration == null) {
            // If the clientRegistration is null, we assume it's a delete operation
            clientRegistrationIdToKeyId.delete(clientRegistrationId);
        }

        if (oldKid != null) {
            // For replace or delete operations, we remove the old key
            kidToJwk.remove(oldKid);
        }

        // Only generates a new key if the clientRegistration is not null (for add and replace operations)
        generateAndAddKey(clientRegistration);
    }

    public JWKSet getJwkSet() {
        return new JWKSet(new ArrayList<>(kidToJwk.values()));
    }

    private void generateOAuth2ClientKeys() {
        onlineCourseConfigurationService.getAllClientRegistrations().forEach(this::generateAndAddKey);
    }

    private void generateAndAddKey(ClientRegistration clientRegistration) {
        if (clientRegistration == null) {
            return;
        }
        try {
            KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
            String kid = kidGenerator.generateKey();

            RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic()).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256).privateKey(keyPair.getPrivate())
                    .keyID(kid).build();

            clientRegistrationIdToKeyId.put(clientRegistration.getRegistrationId(), kid);
            kidToJwk.put(kid, rsaKey);

        }
        catch (NoSuchAlgorithmException e) {
            log.error("Failed to generate key for clientRegistrationId {}", clientRegistration.getRegistrationId(), e);
        }
    }
}
