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

    /**
     * Disables JGit's packed Git memory-mapped file feature to prevent issues when deleting repositories on NFS filesystems.
     * This configuration is applied early in the application startup process, so it ran before any repository interaction.
     */
    @PostConstruct
    public void disablePackedGitMMap() {
        log.debug("Applying JGit configuration: disable packed git mmap");
        WindowCacheConfig cfg = new WindowCacheConfig();
        // This should prevent issues when deleting repositories on NFS filesystems
        // In production we encountered a JGit exception when deleting repositories indicating that .pack files in the repository still have a lock.
        // Further investigation showed that the Artemis app itself holds this lock.
        // This is caused by JGit holding the .pack files in a global WindowCache.
        // This is only cleared when the garbage collector runs which is non-deterministic.
        // So, we disable this performance optimization which does not negatively affect performance in our use case.
        cfg.setPackedGitMMAP(false);
        cfg.install();
    }
}
