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

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;

import de.tum.in.www1.artemis.service.OnlineCourseConfigurationService;
import uk.ac.ox.ctl.lti13.KeyPairService;

/**
 * This Service is responsible to manage JWKs for all OAuth2 ClientRegistrations.
 * On initialisation, each ClientRegistration gets assigned a fresh generated RSAKey.
 */
@Component
public class OAuth2JWKSService implements KeyPairService {

    private final OnlineCourseConfigurationService onlineCourseConfigurationService;

    private final StringKeyGenerator kidGenerator = new Base64StringKeyGenerator(32);

    private final Logger log = LoggerFactory.getLogger(OAuth2JWKSService.class);

    private final Map<String, String> clientRegistrationIdToKeyId = new HashMap<>();

    private JWKSet jwkSet;

    public OAuth2JWKSService(OnlineCourseConfigurationService onlineCourseConfigurationService) throws NoSuchAlgorithmException {
        this.onlineCourseConfigurationService = onlineCourseConfigurationService;
        this.jwkSet = new JWKSet(generateOAuth2ClientKeys());
    }

    @Override
    public KeyPair getKeyPair(String clientRegistrationId) {
        try {
            JWK jwk = this.getJWK(clientRegistrationId);
            return jwk != null ? jwk.toRSAKey().toKeyPair() : null;
        }
        catch (JOSEException e) {
            log.error(e.getMessage());
            return null;
        }
    }

    @Override
    public String getKeyId(String clientRegistrationId) {
        JWK jwk = this.getJWK(clientRegistrationId);
        return jwk != null ? jwk.getKeyID() : null;
    }

    public JWK getJWK(String clientRegistrationId) {
        return this.jwkSet.getKeyByKeyId(this.clientRegistrationIdToKeyId.get(clientRegistrationId));
    }

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

        if (clientRegistration != null) {
            try {
                keys.add(generateKey(clientRegistration));
            }
            catch (NoSuchAlgorithmException e) {
                log.error("Could not generate key for clientRegistrationId {}", clientRegistration.getRegistrationId());
            }
        }
        this.jwkSet = new JWKSet(keys);
    }

    public JWKSet getJwkSet() {
        return this.jwkSet;
    }

    private List<JWK> generateOAuth2ClientKeys() {

        List<JWK> keys = new LinkedList<>();

        for (ClientRegistration clientRegistration : onlineCourseConfigurationService.getAllClientRegistrations()) {
            try {
                if (clientRegistration != null) {
                    keys.add(generateKey(clientRegistration));
                }
            }
            catch (NoSuchAlgorithmException e) {
                log.error("Could not generate key for clientRegistrationId {}", clientRegistration.getRegistrationId());
            }
        }

        return keys;
    }

    private JWK generateKey(ClientRegistration clientRegistration) throws NoSuchAlgorithmException {
        KeyPair clientKeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        String kid = kidGenerator.generateKey();

        RSAKey.Builder builder = new RSAKey.Builder((RSAPublicKey) clientKeyPair.getPublic()).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256)
                .privateKey(clientKeyPair.getPrivate()).keyID(kid);

        this.clientRegistrationIdToKeyId.put(clientRegistration.getRegistrationId(), kid);

        return builder.build();
    }
}
