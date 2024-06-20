package de.tum.in.www1.artemis.service.connectors.localci.buildagent;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_BUILDAGENT;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Optional;

import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.config.icl.ssh.HashUtils;
import de.tum.in.www1.artemis.exception.GitException;

@Service
@Profile(PROFILE_BUILDAGENT)
public class BuildAgentSSHKeyService {

    private static final Logger log = LoggerFactory.getLogger(BuildAgentSSHKeyService.class);

    private KeyPair keyPair;

    @Value("${artemis.version-control.ssh-private-key-folder-path:#{null}}")
    protected Optional<String> gitSshPrivateKeyPath;

    @Value("${artemis.version-control.build-agent-use-ssh:false}")
    private boolean useSSHForBuildAgent;

    @EventListener(ApplicationReadyEvent.class)
    public void applicationReady() {
        if (useSSHForBuildAgent) {
            log.info("Using SSH for build agent authentication.");
        }
        if (!useSSHForBuildAgent) {
            return;
        }

        if (gitSshPrivateKeyPath.isEmpty()) {
            throw new GitException("No SSH private key folder was set but should use SSH for build agent authentication.");
        }

        KeyPairGenerator generator;
        try {
            generator = KeyPairGenerator.getInstance("EC");
            ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256r1");
            generator.initialize(ecSpec);
        }
        catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
            throw new RuntimeException(e);
        }

        log.debug("Starting key pair generation");
        keyPair = generator.generateKeyPair();
        log.debug("Key pair successfully generated");

        try {
            Path privateKeyPath = Path.of(gitSshPrivateKeyPath.orElseThrow(), "id_ecdsa");
            Files.writeString(privateKeyPath, getPrivateKeyAsString());
            Files.setPosixFilePermissions(privateKeyPath, PosixFilePermissions.fromString("rw-------"));

            Path publicKeyPath = Path.of(gitSshPrivateKeyPath.orElseThrow(), "id_ecdsa.pub");
            Files.writeString(publicKeyPath, getPublicKeyAsString());
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String getPrivateKeyAsString() {
        Base64.Encoder encoder = Base64.getEncoder();

        return String.format("""
                -----BEGIN PRIVATE KEY-----
                %s
                -----END PRIVATE KEY-----
                """, encoder.encodeToString(keyPair.getPrivate().getEncoded()));
    }

    /**
     * Returns the fingerprint of the SSH public key
     *
     * @return the SHA 512 fingerprint of the SSH public key. null if SSH should not be used for cloning with this build agent.
     */
    public String getPublicKeyHash() {
        if (!shouldUseSSHForBuildAgent()) {
            return null;
        }

        // Transform public key into another format used for authentication
        AuthorizedKeyEntry keyEntry = AuthorizedKeyEntry.parseAuthorizedKeyEntry(getPublicKeyAsString());
        PublicKey publicKey;
        try {
            publicKey = keyEntry.resolvePublicKey(null, null, null);
        }
        catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException(e);
        }

        return HashUtils.getSha512Fingerprint(publicKey);
    }

    private boolean shouldUseSSHForBuildAgent() {
        return useSSHForBuildAgent;
    }

    /*
     * Formats the PublicKey to meet the requirements of the id_ecdsa.pub file
     */
    private String getPublicKeyAsString() {
        byte[] publicKeyBytes = keyPair.getPublic().getEncoded();
        String publicKeyBase64 = Base64.getEncoder().encodeToString(prepareSshPublicKey(publicKeyBytes));
        return String.format("ecdsa-sha2-nistp256 %s", publicKeyBase64);
    }

    private byte[] prepareSshPublicKey(byte[] publicKeyBytes) {
        int keyLength = 32;
        byte[] x = new byte[keyLength];
        byte[] y = new byte[keyLength];
        System.arraycopy(publicKeyBytes, 27, x, 0, keyLength);
        System.arraycopy(publicKeyBytes, 27 + keyLength, y, 0, keyLength);

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(sshString("ecdsa-sha2-nistp256"));
            out.write(sshString("nistp256"));
            out.write(sshString(x, y));
            return out.toByteArray();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] sshString(String str) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(intToBytes(str.length()));
        out.write(str.getBytes());
        return out.toByteArray();
    }

    private byte[] sshString(byte[] x, byte[] y) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(intToBytes(x.length + y.length + 1));
        out.write(0x04);
        out.write(x);
        out.write(y);
        return out.toByteArray();
    }

    private byte[] intToBytes(int value) {
        return new byte[] { (byte) ((value >>> 24) & 0xFF), (byte) ((value >>> 16) & 0xFF), (byte) ((value >>> 8) & 0xFF), (byte) (value & 0xFF) };
    }
}
