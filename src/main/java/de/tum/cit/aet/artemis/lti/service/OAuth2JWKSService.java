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
import org.springframework.context.annotation.Lazy;
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
@Lazy
@Service
@Profile(PROFILE_LTI)
public class OAuth2JWKSService {

    private final OnlineCourseConfigurationService onlineCourseConfigurationService;

    private final HazelcastInstance hazelcastInstance;

    private final StringKeyGenerator kidGenerator = new Base64StringKeyGenerator(32);

    private static final Logger log = LoggerFactory.getLogger(OAuth2JWKSService.class);

    private IMap<String, JWK> clientRegistrationIdToJwk;

    public OAuth2JWKSService(OnlineCourseConfigurationService onlineCourseConfigurationService, HazelcastInstance hazelcastInstance) {
        this.onlineCourseConfigurationService = onlineCourseConfigurationService;
        this.hazelcastInstance = hazelcastInstance;
    }

    /**
     * Initializes the clientRegistration to JWK Map for OAuth2 ClientRegistrations.
     * This method is called once when the application starts, and it generates keys for all existing ClientRegistrations.
     */
    @PostConstruct
    public void init() {
        clientRegistrationIdToJwk = hazelcastInstance.getMap("ltiJwkMap");

        // Only one node should initialize the JWKSet
        if (clientRegistrationIdToJwk.isEmpty()) {
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
        return clientRegistrationIdToJwk.get(clientRegistrationId);
    }

    /**
     * Updates the JWK associated with the clientRegistrationId receives. Can either remove, replace, or add a new one.
     *
     * @param clientRegistrationId the client registrationId
     */
    public void updateKey(String clientRegistrationId) {
        ClientRegistration clientRegistration = onlineCourseConfigurationService.findByRegistrationId(clientRegistrationId);

        if (clientRegistration == null) {
            clientRegistrationIdToJwk.delete(clientRegistrationId);  // Delete only
        }
        else {
            generateAndAddKey(clientRegistration);  // Replace or Add
        }
    }

    /**
     * Returns the JWKSet containing all JWKs for the OAuth2 ClientRegistrations.
     *
     * @return a JWKSet containing all JWKs
     */
    public JWKSet getJwkSet() {
        return new JWKSet(new ArrayList<>(clientRegistrationIdToJwk.values()));
    }

    /**
     * Generates a new JWK for each OAuth2 ClientRegistration and stores it in the Hazelcast map.
     * This method is called once during initialization to ensure all existing ClientRegistrations have a key.
     */
    private void generateOAuth2ClientKeys() {
        onlineCourseConfigurationService.getAllClientRegistrations().forEach(this::generateAndAddKey);
    }

    /**
     * Generates a new RSAKey for the given ClientRegistration and adds it to the Hazelcast map.
     * If the ClientRegistration is null, no action is taken.
     *
     * @param clientRegistration the ClientRegistration for which to generate a key
     */
    private void generateAndAddKey(ClientRegistration clientRegistration) {
        if (clientRegistration == null) {
            return;
        }
        try {
            KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
            String kid = kidGenerator.generateKey();

            RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic()).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256).privateKey(keyPair.getPrivate())
                    .keyID(kid).build();

            clientRegistrationIdToJwk.put(clientRegistration.getRegistrationId(), rsaKey);
        }
        catch (NoSuchAlgorithmException e) {
            log.error("Failed to generate key for clientRegistrationId {}", clientRegistration.getRegistrationId(), e);
        }
    }
}
