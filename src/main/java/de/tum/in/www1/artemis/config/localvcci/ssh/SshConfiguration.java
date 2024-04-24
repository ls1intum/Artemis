package de.tum.in.www1.artemis.config.localvcci.ssh;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_LOCALVC;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.apache.sshd.common.util.threads.ThreadUtils;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;

import de.tum.in.www1.artemis.service.ResourceLoaderService;

@Profile(PROFILE_LOCALVC)
@Configuration
public class SshConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SshConfiguration.class);

    @Value("${artemis.version-control.ssh-port:7921}")
    private int sshPort;

    @Value("${artemis.version-control.ssh-host-key-path:null}")
    private Optional<Path> sshHostKeyPath;

    @Value("${server.url}")
    private String artemisServerUrl;

    private final GitPublickeyAuthenticator gitPublickeyAuthenticator;

    private final ResourceLoaderService resourceLoaderService;

    private final SshGitCommandFactory sshGitCommandFactory;

    private final SshGitLocationResolver sshGitLocationResolver;

    public SshConfiguration(GitPublickeyAuthenticator gitPublickeyAuthenticator, ResourceLoaderService resourceLoaderService, SshGitCommandFactory sshGitCommandFactory,
            SshGitLocationResolver sshGitLocationResolver) {
        this.gitPublickeyAuthenticator = gitPublickeyAuthenticator;
        this.resourceLoaderService = resourceLoaderService;
        this.sshGitCommandFactory = sshGitCommandFactory;
        this.sshGitLocationResolver = sshGitLocationResolver;
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    public SshServer sshServer() throws IOException {
        SshServer sshd = SshServer.setUpDefaultServer();
        sshd.setPort(sshPort);

        if (sshHostKeyPath.isPresent() && sshHostKeyPath.get().toFile().exists()) {
            // this allows to customize the host key file for production environments
            log.info("Use SSH host key {}", sshHostKeyPath.get());
            Resource hostKeyResource = resourceLoaderService.getResource(sshHostKeyPath.get());
            if (!hostKeyResource.getFile().exists()) {
                log.error("host key {} does not exist", hostKeyResource.getFile().getAbsolutePath());
            }
            sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(hostKeyResource.getFile().toPath()));
        }
        else {
            // this is a simple solution for development, the host key will be generated during the first ssh operation in case it does not exist
            sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(Paths.get("tmp", "hostkey.ser")));
        }
        sshd.setCommandFactory(
                sshGitCommandFactory.withGitLocationResolver(sshGitLocationResolver).withExecutorServiceProvider(() -> ThreadUtils.newFixedThreadPool("git-ssh", 8)));
        // sshd.setCommandFactory(gitCommandFactory());
        sshd.setPublickeyAuthenticator(gitPublickeyAuthenticator);
        // Add command factory or shell here to handle Git commands or any other commands

        // TODO: This is deprecated
        URL serverUrl = new URL(artemisServerUrl);
        log.info("Started git ssh server on ssh://{}:{}", serverUrl.getHost(), sshPort);

        return sshd;
    }
}
