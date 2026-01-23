package de.tum.cit.aet.artemis.lti.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LTI;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.keygen.Base64StringKeyGenerator;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.stereotype.Service;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;

import de.tum.cit.aet.artemis.programming.service.localci.DistributedDataAccessService;

/**
 * This Service is responsible to manage JWKs for all OAuth2 ClientRegistrations.
 * On initialisation, each ClientRegistration gets assigned a fresh generated RSAKey.
 */
@Lazy
@Service
@Profile(PROFILE_LTI)
public class OAuth2JWKSService {

    private final OnlineCourseConfigurationService onlineCourseConfigurationService;

    private final DistributedDataAccessService distributedDataAccessService;

    private final StringKeyGenerator kidGenerator = new Base64StringKeyGenerator(32);

    private static final Logger log = LoggerFactory.getLogger(OAuth2JWKSService.class);

    public OAuth2JWKSService(OnlineCourseConfigurationService onlineCourseConfigurationService, DistributedDataAccessService distributedDataAccessService) {
        this.onlineCourseConfigurationService = onlineCourseConfigurationService;
        this.distributedDataAccessService = distributedDataAccessService;
    }

    /**
     * Returns the JWK created for a clientRegistrationId
     *
     * @param clientRegistrationId the client registrationId
     * @return the JWK found for the client registrationId
     */
    public JWK getJWK(String clientRegistrationId) {
        return distributedDataAccessService.getClientRegistrationIdToJwk().get(clientRegistrationId);
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
            distributedDataAccessService.getDistributedClientRegistrationIdToJwk().remove(clientRegistrationId);
        }
        else {
            distributedDataAccessService.getDistributedClientRegistrationIdToJwk().put(clientRegistrationId, generateKey(clientRegistration));
        }
    }

    /**
     * Returns the JWKSet containing all JWKs for the OAuth2 ClientRegistrations.
     *
     * @return a JWKSet containing all JWKs
     */
    public JWKSet getJwkSet() {
        return new JWKSet(new ArrayList<>(distributedDataAccessService.getClientRegistrationIdToJwk().values()));
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
