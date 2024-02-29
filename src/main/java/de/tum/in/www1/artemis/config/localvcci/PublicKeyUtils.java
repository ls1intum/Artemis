package de.tum.in.www1.artemis.config.localvcci;

import java.security.PublicKey;
import java.util.Base64;

public class PublicKeyUtils {

    public static String encodePublicKey(PublicKey publicKey) {
        // Assuming RSA key for simplicity; adjust as needed for other types
        return "ssh-rsa " + Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }
}
