package de.tum.in.www1.artemis.config.localvcci.ssh;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_LOCALVC;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import org.springframework.context.annotation.Profile;

@Profile(PROFILE_LOCALVC)
public class HashUtils {

    public static String hashString(String input) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-512");
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }
}
