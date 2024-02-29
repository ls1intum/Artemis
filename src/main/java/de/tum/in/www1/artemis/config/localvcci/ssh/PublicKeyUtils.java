package de.tum.in.www1.artemis.config.localvcci.ssh;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_LOCALVC;

import java.security.PublicKey;
import java.util.Base64;

import org.springframework.context.annotation.Profile;

@Profile(PROFILE_LOCALVC)
public class PublicKeyUtils {

    public static String encodePublicKey(PublicKey publicKey) {
        // Assuming RSA key for simplicity; adjust as needed for other types
        return "ssh-rsa " + Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }
}
