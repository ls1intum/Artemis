package de.tum.cit.aet.artemis.core.config;

import jakarta.annotation.PostConstruct;

import org.eclipse.jgit.storage.file.WindowCacheConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;

@Configuration
// this needs to run as early as possible, so JGit is properly configured
@Lazy(value = false)
@Profile({ Constants.PROFILE_CORE, Constants.PROFILE_BUILDAGENT })
public class JGitConfig {

    private static final Logger log = LoggerFactory.getLogger(JGitConfig.class);

    @PostConstruct
    public void disablePackedGitMMap() {
        log.debug("Applying JGit configuration: disable packed git mmap");
        WindowCacheConfig cfg = new WindowCacheConfig();
        // this should prevent issues when deleting repositories on NFS filesystems
        cfg.setPackedGitMMAP(false);
        cfg.install();
    }
}
