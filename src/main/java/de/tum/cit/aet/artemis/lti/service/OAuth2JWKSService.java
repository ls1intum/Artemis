package de.tum.cit.aet.artemis.lti.service;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
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

import de.tum.cit.aet.artemis.lti.config.LtiEnabled;

/**
 * This Service is responsible to manage JWKs for all OAuth2 ClientRegistrations.
 * On initialisation, each ClientRegistration gets assigned a fresh generated RSAKey.
 */
@Lazy
@Service
@Conditional(LtiEnabled.class)
public class OAuth2JWKSService {

    private final OnlineCourseConfigurationService onlineCourseConfigurationService;

    private final HazelcastInstance hazelcastInstance;

    private final StringKeyGenerator kidGenerator = new Base64StringKeyGenerator(32);

    private static final Logger log = LoggerFactory.getLogger(OAuth2JWKSService.class);

    private IMap<String, JWK> clientRegistrationIdToJwk;

    public OAuth2JWKSService(OnlineCourseConfigurationService onlineCourseConfigurationService, @Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance) {
        this.onlineCourseConfigurationService = onlineCourseConfigurationService;
        this.hazelcastInstance = hazelcastInstance;
    }

    /**
     * Returns the Hazelcast map that stores the JWKs for each OAuth2 ClientRegistration.
     * The map is lazily initialized when it is first accessed.
     *
     * @return the Hazelcast map containing JWKs
     */
    public IMap<String, JWK> getClientRegistrationIdToJwk() {
        if (this.clientRegistrationIdToJwk == null) {
            this.clientRegistrationIdToJwk = this.hazelcastInstance.getMap("ltiJwkMap");
        }
        return this.clientRegistrationIdToJwk;
    }

    /**
     * Returns the JWK created for a clientRegistrationId
     *
     * @param clientRegistrationId the client registrationId
     * @return the JWK found for the client registrationId
     */
    public JWK getJWK(String clientRegistrationId) {
        return getClientRegistrationIdToJwk().get(clientRegistrationId);
    }

    /**
     * Updates the JWK associated with the clientRegistrationId receives.
     * If the clientRegistrationId does not exist, the key for it is deleted.
     * Otherwise, a new key is generated and stored in the Hazelcast map.
     *
     * @param clientRegistrationId the client registrationId
     */
    public void updateKey(String clientRegistrationId) {
        ClientRegistration clientRegistration = onlineCourseConfigurationService.findByRegistrationId(clientRegistrationId);

        if (clientRegistration == null) {
            getClientRegistrationIdToJwk().delete(clientRegistrationId);
        }
        else {
            getClientRegistrationIdToJwk().put(clientRegistrationId, generateKey(clientRegistration));
        }
    }

    /**
     * Returns the JWKSet containing all JWKs for the OAuth2 ClientRegistrations.
     *
     * @return a JWKSet containing all JWKs
     */
    public JWKSet getJwkSet() {
        return new JWKSet(new ArrayList<>(getClientRegistrationIdToJwk().values()));
    }

    /**
     * Generates a new RSAKey for the given ClientRegistration.
     *
     * @param clientRegistration the ClientRegistration for which to generate a key
     * @return a new RSAKey with a generated key ID (kid)
     */
    public RSAKey generateKey(ClientRegistration clientRegistration) {
        try {
            KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
            String kid = kidGenerator.generateKey();

            return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic()).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256).privateKey(keyPair.getPrivate()).keyID(kid)
                    .build();
        }
        catch (NoSuchAlgorithmException e) {
            log.error("Failed to generate key for clientRegistrationId {}", clientRegistration.getRegistrationId(), e);
            return null;
        }
    }
}
