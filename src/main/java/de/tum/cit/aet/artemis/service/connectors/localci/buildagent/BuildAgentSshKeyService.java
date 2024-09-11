package de.tum.cit.aet.artemis.service.connectors.localci.buildagent;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_BUILDAGENT;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyEncryptionContext;
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyPairResourceWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
@Profile(PROFILE_BUILDAGENT)
public class BuildAgentSshKeyService {

    private static final Logger log = LoggerFactory.getLogger(BuildAgentSshKeyService.class);

    private KeyPair keyPair;

    @Value("${artemis.version-control.ssh-private-key-folder-path:#{null}}")
    private Optional<String> gitSshPrivateKeyPath;

    @Value("${artemis.version-control.build-agent-use-ssh:false}")
    private boolean useSshForBuildAgent;

    @Value("${info.contact}")
    private String sshKeyComment;

    /**
     * Generates the SSH key pair and writes the private key when the application is started and the build agents should use SSH for their git operations.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void applicationReady() {
        if (!useSshForBuildAgent) {
            return;
        }

        log.info("Using SSH for build agent authentication.");

        if (gitSshPrivateKeyPath.isEmpty()) {
            throw new RuntimeException("No SSH private key folder was set but should use SSH for build agent authentication.");
        }

        try {
            generateKeyPair();
            writePrivateKey();
        }
        catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    private void generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(4096);
        keyPair = keyGen.generateKeyPair();
    }

    private void writePrivateKey() throws IOException, GeneralSecurityException {
        Path privateKeyPath = Path.of(gitSshPrivateKeyPath.orElseThrow(), "id_rsa");
        OpenSSHKeyPairResourceWriter writer = new OpenSSHKeyPairResourceWriter();

        try (OutputStream outputStream = Files.newOutputStream(privateKeyPath)) {
            writer.writePrivateKey(keyPair, sshKeyComment, new OpenSSHKeyEncryptionContext(), outputStream);
        }

        // Avoid an UnsupportedOperationException on Windows
        boolean posixSupported = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
        if (posixSupported) {
            Files.setPosixFilePermissions(privateKeyPath, PosixFilePermissions.fromString("rw-------"));
        }
    }

    /**
     * Returns the formated SSH public key.
     * If SSH is not used for the build agent, it returns {@code null}.
     *
     * @return the public key
     */
    public String getPublicKeyAsString() {
        if (!useSshForBuildAgent) {
            return null;
        }

        OpenSSHKeyPairResourceWriter writer = new OpenSSHKeyPairResourceWriter();
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            writer.writePublicKey(keyPair, sshKeyComment, outputStream);
            return outputStream.toString();
        }
        catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }
}
