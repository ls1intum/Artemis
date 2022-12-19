package de.tum.in.www1.artemis.security;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.keygen.Base64StringKeyGenerator;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;

import de.tum.in.www1.artemis.service.OnlineCourseConfigurationService;

/**
 * This Service is responsible to manage JWKs for all OAuth2 ClientRegistrations.
 * On initialisation, each ClientRegistration gets assigned a fresh generated RSAKey.
 */
@Component
public class OAuth2JWKSService {

    private final OnlineCourseConfigurationService onlineCourseConfigurationService;

    private final StringKeyGenerator kidGenerator = new Base64StringKeyGenerator(32);

    private final Logger log = LoggerFactory.getLogger(OAuth2JWKSService.class);

    private final Map<String, String> clientRegistrationIdToKeyId = new HashMap<>();

    private JWKSet jwkSet;

    public OAuth2JWKSService(OnlineCourseConfigurationService onlineCourseConfigurationService) {
        this.onlineCourseConfigurationService = onlineCourseConfigurationService;
        this.jwkSet = new JWKSet(generateOAuth2ClientKeys());
    }

    /**
     * Returns the JWK created for a clientRegistrationId
     *
     * @param clientRegistrationId the client registrationId
     * @return the JWK found for the client registrationId
     */
    public JWK getJWK(String clientRegistrationId) {
        return this.jwkSet.getKeyByKeyId(this.clientRegistrationIdToKeyId.get(clientRegistrationId));
    }

    /**
     * Updates the JWK associated with the clientRegistrationId receives. Can either remove, replace, or add a new one.
     *
     * @param clientRegistrationId the client registrationId
     */
    public void updateKey(String clientRegistrationId) {
        ClientRegistration clientRegistration = onlineCourseConfigurationService.findByRegistrationId(clientRegistrationId);
        JWK jwkToRemove = getJWK(clientRegistrationId);

        if (jwkToRemove == null && clientRegistration == null) {
            return;
        }

        List<JWK> keys = new ArrayList<>(jwkSet.getKeys());

        if (jwkToRemove != null) {
            keys = keys.stream().filter(jwk -> jwk != jwkToRemove).collect(Collectors.toList());
        }

        generateAndAddKey(clientRegistration, keys);
        this.jwkSet = new JWKSet(keys);
    }

    public JWKSet getJwkSet() {
        return this.jwkSet;
    }

    private List<JWK> generateOAuth2ClientKeys() {
        List<JWK> keys = new LinkedList<>();
        onlineCourseConfigurationService.getAllClientRegistrations().forEach(c -> generateAndAddKey(c, keys));
        return keys;
    }

    private void generateAndAddKey(ClientRegistration clientRegistration, List<JWK> keys) {
        if (clientRegistration == null) {
            return;
        }

        KeyPair clientKeyPair;
        try {
            clientKeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        }
        catch (NoSuchAlgorithmException e) {
            log.error("Could not generate key for clientRegistrationId {}", clientRegistration.getRegistrationId());
            return;
        }

        String kid = kidGenerator.generateKey();
        RSAKey.Builder builder = new RSAKey.Builder((RSAPublicKey) clientKeyPair.getPublic()).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256)
                .privateKey(clientKeyPair.getPrivate()).keyID(kid);

        this.clientRegistrationIdToKeyId.put(clientRegistration.getRegistrationId(), kid);

        JWK jwk = builder.build();
        keys.add(jwk);
    }
}
