package de.tum.cit.aet.artemis.programming.service.localvc.ssh;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PublicKey;

import org.apache.commons.codec.digest.MessageDigestAlgorithms;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.digest.BuiltinDigests;

public class HashUtils {

    public static String getSha512Fingerprint(PublicKey key) {
        return KeyUtils.getFingerPrint(BuiltinDigests.sha512.create(), key);
    }

    public static String getSha256Fingerprint(PublicKey key) {
        return KeyUtils.getFingerPrint(BuiltinDigests.sha256.create(), key);
    }

    public static byte[] hashSha256(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance(MessageDigestAlgorithms.SHA_256);
            return digest.digest(secret.getBytes(StandardCharsets.UTF_8));
        }
        catch (Exception e) {
            throw new IllegalStateException("Failed to hash token", e);
        }
    }
}
