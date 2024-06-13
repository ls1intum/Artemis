package de.tum.in.www1.artemis.service.connectors.localci.buildagent;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_BUILDAGENT;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Optional;

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

    @Value("${artemis.version-control.build-agent-use-ssh:true}")
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
            generator = KeyPairGenerator.getInstance("RSA");
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        generator.initialize(4096);

        keyPair = generator.generateKeyPair();

        try {
            Files.write(Path.of(gitSshPrivateKeyPath.orElseThrow(), "private.key"), keyPair.getPrivate().getEncoded());
            Files.write(Path.of(gitSshPrivateKeyPath.orElseThrow(), "public.pub"), keyPair.getPublic().getEncoded());
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<PrivateKey> getPrivateKey() {
        if (!shouldUseSSHForBuildAgent()) {
            return Optional.empty();
        }

        return Optional.of(keyPair.getPrivate());
    }

    public Optional<PublicKey> getPublicKey() {
        if (!shouldUseSSHForBuildAgent()) {
            return Optional.empty();
        }

        return Optional.of(keyPair.getPublic());
    }

    public boolean shouldUseSSHForBuildAgent() {
        return useSSHForBuildAgent;
    }
}
