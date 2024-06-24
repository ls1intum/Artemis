package de.tum.in.www1.artemis.service.connectors.localci.buildagent;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_BUILDAGENT;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
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

    @Value("${info.contact}")
    private String sshKeyComment;

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

        generateKeyPair();

        try {
            writePrivateKey();
            writePublicKey();
        }
        catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    private void generateKeyPair() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(4096);
            keyPair = keyGen.generateKeyPair();
        }
        catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    private void writePrivateKey() throws IOException, GeneralSecurityException {
        Path privateKeyPath = Path.of(gitSshPrivateKeyPath.orElseThrow(), "id_rsa");
        OpenSSHKeyPairResourceWriter writer = new OpenSSHKeyPairResourceWriter();

        try (OutputStream outputStream = Files.newOutputStream(privateKeyPath)) {
            writer.writePrivateKey(keyPair, sshKeyComment, new OpenSSHKeyEncryptionContext(), outputStream);
        }

        Files.setPosixFilePermissions(privateKeyPath, PosixFilePermissions.fromString("rw-------"));
    }

    private void writePublicKey() throws IOException, GeneralSecurityException {
        Path publicKeyPath = Path.of(gitSshPrivateKeyPath.orElseThrow(), "id_rsa.pub");
        OpenSSHKeyPairResourceWriter writer = new OpenSSHKeyPairResourceWriter();

        try (OutputStream outputStream = Files.newOutputStream(publicKeyPath)) {
            writer.writePublicKey(keyPair, sshKeyComment, outputStream);
        }
    }

    public String getPublicKeyAsString() {
        if (!shouldUseSSHForBuildAgent()) {
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

    private boolean shouldUseSSHForBuildAgent() {
        return useSSHForBuildAgent;
    }
}
