package de.tum.cit.aet.artemis.localvc.service.ssh;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.sshd.common.util.threads.SshThreadPoolExecutor;
import org.apache.sshd.common.util.threads.SshdThreadFactory;
import org.apache.sshd.common.util.threads.ThreadUtils;
import org.apache.sshd.core.CoreModuleProperties;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;

import de.tum.cit.aet.artemis.localvc.service.GitPublickeyAuthenticatorService;
import de.tum.cit.aet.artemis.localvc.service.SshGitCommandFactoryService;
import de.tum.cit.aet.artemis.localvc.service.SshGitLocationResolverService;

/**
 * Configuration of the SSH server for Local Version Control (LocalVC).
 * This server handles SSH requests for Git operations.
 * It is only active when the 'localvc' profile is enabled.
 * We have to use @Lazy(false) here, as the SSH server needs to be started at application startup otherwise ssh operations get a connection refused.
 */
@Profile(PROFILE_LOCALVC)
@Configuration
@Lazy(value = false)
public class SshConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SshConfiguration.class);

    /** The git commands (clone, fetch, push) that run at the same time. Further commands wait for a free thread. */
    private static final int MAX_CONCURRENT_GIT_COMMANDS = 256;

    /** The commands that may wait for a free thread. Further commands are rejected. */
    private static final int MAX_QUEUED_GIT_COMMANDS = 256;

    private static final int AUTHENTICATION_TIMEOUT_SECONDS = 60;

    /** A git command uses one channel, so the limit is generous. */
    private static final int MAX_CHANNELS_PER_SESSION = 64;

    /** The sessions that one client address keeps open. It is generous because many users of one institution can share an address. */
    private static final int MAX_SESSIONS_PER_ADDRESS = 128;

    @Value("${artemis.version-control.ssh-port:7921}")
    private int sshPort;

    @Value("${artemis.version-control.ssh-host-key-path:null}")
    private Optional<Path> sshHostKeyPath;

    @Value("${server.url}")
    private String artemisServerUrl;

    private final GitPublickeyAuthenticatorService gitPublickeyAuthenticatorService;

    private final SshGitCommandFactoryService sshGitCommandFactoryService;

    private final SshGitLocationResolverService sshGitLocationResolverService;

    private final ProxyProtocolAcceptor proxyProtocolAcceptor;

    public SshConfiguration(GitPublickeyAuthenticatorService gitPublickeyAuthenticatorService, SshGitCommandFactoryService sshGitCommandFactoryService,
            SshGitLocationResolverService sshGitLocationResolverService, ProxyProtocolAcceptor proxyProtocolAcceptor) {
        this.gitPublickeyAuthenticatorService = gitPublickeyAuthenticatorService;
        this.sshGitCommandFactoryService = sshGitCommandFactoryService;
        this.sshGitLocationResolverService = sshGitLocationResolverService;
        this.proxyProtocolAcceptor = proxyProtocolAcceptor;
    }

    /**
     * Configure and start the SSH server for LocalVC
     *
     * @return the configured SSH server
     */
    @Bean(initMethod = "start", destroyMethod = "stop")
    public SshServer sshServer() {
        SshServer sshd = SshServer.setUpDefaultServer();
        sshd.setPort(sshPort);

        if (sshHostKeyPath.isPresent() && sshHostKeyPath.get().toFile().exists()) {
            // this allows to customize the host key file for production environments
            log.info("Use SSH host key {}", sshHostKeyPath.get());
            sshd.setKeyPairProvider(new MultipleHostKeyProvider(sshHostKeyPath.get()));
        }
        else {
            // this is a simple solution for development, the host key will be generated during the first ssh operation in case it does not exist

            sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(Path.of("local", "tmp", "hostkey.ser")));
        }
        // One bounded pool serves all git commands: a fixed number of threads and a finite queue. A command that finds the queue full is rejected instead of waiting
        // without limit. The pool is shared, so a command must not shut it down. Every command gets a wrapper of its own that ignores the shutdown: a command shuts its executor
        // down when it ends, and a wrapper that is shared would then refuse every later command. The pool is shut down together with the server.
        @SuppressWarnings("java:S2095") // the pool is shut down by the close listener of the server below
        SshThreadPoolExecutor gitCommandPool = new SshThreadPoolExecutor(MAX_CONCURRENT_GIT_COMMANDS, MAX_CONCURRENT_GIT_COMMANDS, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(MAX_QUEUED_GIT_COMMANDS), new SshdThreadFactory("git-ssh"), (task, executor) -> {
                    if (!executor.isShutdown()) {
                        log.warn("A git command over SSH was rejected: all {} threads are busy and {} commands are waiting", MAX_CONCURRENT_GIT_COMMANDS, MAX_QUEUED_GIT_COMMANDS);
                    }
                    throw new RejectedExecutionException("All threads for git commands over SSH are busy");
                });
        sshd.addCloseFutureListener(_ -> gitCommandPool.shutdown());
        sshd.setCommandFactory(
                sshGitCommandFactoryService.withGitLocationResolver(sshGitLocationResolverService).withExecutorServiceProvider(() -> ThreadUtils.noClose(gitCommandPool)));
        // Clients that do not authenticate quickly are dropped, and the channels of a session are bounded, instead of holding resources of the server.
        CoreModuleProperties.AUTH_TIMEOUT.set(sshd, Duration.ofSeconds(AUTHENTICATION_TIMEOUT_SECONDS));
        CoreModuleProperties.MAX_CONCURRENT_CHANNELS.set(sshd, MAX_CHANNELS_PER_SESSION);
        sshd.addSessionListener(new SshSessionLimiter(MAX_SESSIONS_PER_ADDRESS));
        sshd.setPublickeyAuthenticator(gitPublickeyAuthenticatorService);
        // Recovers the real client address when a load balancer forwards this port at the TCP level. Installed
        // unconditionally: without configured proxy protocol sources it only removes headers, so that a proxy with
        // proxy_protocol on does not break ssh for a node that does not list it.
        sshd.setServerProxyAcceptor(proxyProtocolAcceptor);
        // Add command factory or shell here to handle Git commands or any other commands

        try {
            var serverUri = new URI(artemisServerUrl);
            log.info("Started git ssh server on ssh://{}:{}", serverUri.getHost(), sshPort);
        }
        catch (URISyntaxException e) {
            log.error("Failed to parse server URL", e);
        }

        return sshd;
    }
}
