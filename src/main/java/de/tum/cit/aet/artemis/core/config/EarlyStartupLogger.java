package de.tum.cit.aet.artemis.core.config;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.ArtemisApp;
import de.tum.cit.aet.artemis.core.util.TimeLogUtil;

@Component
public class EarlyStartupLogger implements ApplicationListener<WebServerInitializedEvent> {

    private static final Logger log = LoggerFactory.getLogger(EarlyStartupLogger.class);

    private final Environment env;

    private final BuildProperties buildProperties;

    private final GitProperties gitProperties;

    public EarlyStartupLogger(Environment env, BuildProperties buildProperties, GitProperties gitProperties) {
        this.env = env;
        this.buildProperties = buildProperties;
        this.gitProperties = gitProperties;
    }

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        String protocol = "http";
        if (env.getProperty("server.ssl.key-store") != null) {
            protocol = "https";
        }
        String serverPort = env.getProperty("server.port");
        String version = buildProperties.getVersion();
        String gitCommitId = gitProperties.getShortCommitId();
        String gitBranch = gitProperties.getBranch();
        String contextPath = env.getProperty("server.servlet.context-path");
        if (StringUtils.isBlank(contextPath)) {
            contextPath = "/";
        }
        String hostAddress = "localhost";
        try {
            hostAddress = InetAddress.getLocalHost().getHostAddress();
        }
        catch (UnknownHostException e) {
            log.warn("The host name could not be determined, using `localhost` as fallback");
        }
        log.info("""

                ----------------------------------------------------------
                \t'{}' is running! Access URLs:
                \tLocal:        {}://localhost:{}{}
                \tExternal:     {}://{}:{}{}
                \tProfiles:     {}
                \tVersion:      {}
                \tGit Commit:   {}
                \tGit Branch:   {}
                \tFull startup: {}
                ----------------------------------------------------------

                """, env.getProperty("spring.application.name"), protocol, serverPort, contextPath, protocol, hostAddress, serverPort, contextPath,
                env.getActiveProfiles().length == 0 ? env.getDefaultProfiles() : env.getActiveProfiles(), version, gitCommitId, gitBranch,
                TimeLogUtil.formatDurationFrom(ArtemisApp.appStart));
    }
}
