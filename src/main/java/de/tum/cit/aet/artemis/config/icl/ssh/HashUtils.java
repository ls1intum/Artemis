package de.tum.cit.aet.artemis.config.icl.ssh;

import java.security.PublicKey;

import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.digest.BuiltinDigests;

public class HashUtils {

    public static String getSha512Fingerprint(PublicKey key) {
        return KeyUtils.getFingerPrint(BuiltinDigests.sha512.create(), key);
    }
}
