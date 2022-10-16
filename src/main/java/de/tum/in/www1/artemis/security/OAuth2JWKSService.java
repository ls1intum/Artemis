package de.tum.in.www1.artemis.security;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.keygen.Base64StringKeyGenerator;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;

import uk.ac.ox.ctl.lti13.KeyPairService;

/**
 * This Service is responsible to manage JWKs for all OAuth2 ClientRegistrations.
 *
 * On initialisation, each ClientRegistration gets assigned a fresh generated RSAKey.
 */
@Component
public class OAuth2JWKSService implements KeyPairService {

    private ClientRegistrationRepository clientRegistrationRepository;

    private StringKeyGenerator kidGenerator = new Base64StringKeyGenerator(32);

    private Logger log = LoggerFactory.getLogger(OAuth2JWKSService.class);

    private JWKSet jwkSet;

    private Map<String, String> clientRegistrationIdToKeyId = new HashMap<>();

    public OAuth2JWKSService(ClientRegistrationRepository clientRegistrationRepository) throws NoSuchAlgorithmException {
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.jwkSet = new JWKSet(generateOAuth2ClientKeys());
    }

    @Override
    public KeyPair getKeyPair(String clientRegistrationId) {
        try {
            JWK jwk = this.getJWK(clientRegistrationRepository.findByRegistrationId(clientRegistrationId));
            return jwk != null ? jwk.toRSAKey().toKeyPair() : null;
        }
        catch (JOSEException e) {
            log.error(e.getMessage());
            return null;
        }
    }

    @Override
    public String getKeyId(String clientRegistration) {
        JWK jwk = this.getJWK(clientRegistrationRepository.findByRegistrationId(clientRegistration));
        return jwk != null ? jwk.getKeyID() : null;
    }

    public JWK getJWK(ClientRegistration clientRegistration) {
        return this.jwkSet.getKeyByKeyId(this.clientRegistrationIdToKeyId.get(clientRegistration.getRegistrationId()));
    }

    public JWKSet getJwkSet() {
        return this.jwkSet;
    }

    private List<JWK> generateOAuth2ClientKeys() throws NoSuchAlgorithmException {
        if (!(this.clientRegistrationRepository instanceof Iterable)) {
            String message = "ClientRegistrationRepository is expected to implement Iterable";
            log.error(message);
            throw new IllegalStateException(message);
        }

        List<JWK> keys = new LinkedList<>();

        for (Object obj : (Iterable<?>) this.clientRegistrationRepository) {
            if (!(obj instanceof ClientRegistration clientRegistration)) {
                String message = "ClientRegistrationRepository must be an Iterable of ClientRegistration";
                log.error(message);
                throw new RuntimeException(message);
            }

            KeyPair clientKeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
            String kid = kidGenerator.generateKey();

            RSAKey.Builder builder = new RSAKey.Builder((RSAPublicKey) clientKeyPair.getPublic()).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256)
                    .privateKey(clientKeyPair.getPrivate()).keyID(kid);

            keys.add(builder.build());
            this.clientRegistrationIdToKeyId.put(clientRegistration.getRegistrationId(), kid);
        }

        return keys;
    }
}
