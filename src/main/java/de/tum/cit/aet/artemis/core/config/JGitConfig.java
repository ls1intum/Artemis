package de.tum.cit.aet.artemis.core.config;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.annotation.PostConstruct;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.storage.file.WindowCacheConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;
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
     * Whether the {@link SystemReader} below has already been installed in this JVM.
     * <p>
     * {@link SystemReader#setInstance} nulls JGit's static platform detection caches ({@code isWindows}, {@code isMacOS},
     * {@code isLinux}) and only then re-derives them, so a call that lands while another thread is doing git work makes
     * that work fail with a {@code NullPointerException}. Installing exactly once, as early as possible, is the whole
     * protection - which is also why the tests install it from their launcher listener before the test plan starts
     * rather than letting the first Spring context do it mid-run.
     */
    private static final AtomicBoolean SYSTEM_READER_CONFIGURED = new AtomicBoolean(false);

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

    /**
     * Stops JGit consulting the machine's git configuration, and stops it re-checking those files on every operation.
     * <p>
     * JGit resolves three configuration files above a repository's own: the system config ({@code /etc/gitconfig}),
     * the user config ({@code ~/.gitconfig} plus its XDG location) and its own {@code ~/.jgitconfig}. Before serving
     * a configuration value it calls {@code SystemReader#updateAll}, which asks each of them whether it has changed -
     * and "has it changed" begins with "does it exist".
     * <p>
     * On a server none of them exist, so each check is a failed {@code lstat} that the JDK turns into a
     * {@code UnixException} and then a {@code NoSuchFileException}, both capturing a stack trace. A JFR recording of
     * one 2000-student benchmark run counted 63,998 {@code NoSuchFileException}s on a single node, 62,308 of them
     * from this path alone. Measured against JGit 7.7.1 in isolation, resolving a repository's configuration costs
     * 27.6 us with the files being checked and 15.4 us without.
     * <p>
     * The cost is the smaller half of the reason. A server must not take its git behaviour from whatever
     * {@code ~/.gitconfig} happens to sit in the service account's home directory: a stray {@code core.autocrlf} or
     * {@code gc.auto} would change how student repositories are written, and could differ from one node to the next.
     * Artemis sets everything it relies on explicitly.
     * <p>
     * The replacements are the configuration JGit itself returns when no system config can be located - backed by no
     * file, never loading, never outdated - so this widens a case JGit already supports from one file to three.
     */
    @PostConstruct
    public void useRepositoryGitConfigurationOnly() {
        configureSystemReaderOnce();
    }

    /**
     * Installs the reader the first time it is called and does nothing afterwards.
     * <p>
     * Public and static because the test launcher installs it before the test plan starts, while the JVM is still
     * single-threaded; the {@code @PostConstruct} above then finds it already configured and does nothing. In
     * production nothing runs before the context, so the {@code @PostConstruct} is the call that installs it.
     *
     * @return true if this call installed the reader, false if it was already installed
     */
    public static boolean configureSystemReaderOnce() {
        if (!SYSTEM_READER_CONFIGURED.compareAndSet(false, true)) {
            return false;
        }
        log.debug("Applying JGit configuration: ignore the system, user and jgit git configuration files");
        SystemReader.setInstance(new RepositoryOnlyConfigReader(SystemReader.getInstance()));
        return true;
    }

    /**
     * Whether the reader has been installed in this JVM.
     *
     * @return true once {@link #configureSystemReaderOnce()} has installed it
     */
    public static boolean isSystemReaderConfigured() {
        return SYSTEM_READER_CONFIGURED.get();
    }

    /**
     * Everything the platform reader does, except that the three configuration files above a repository are empty.
     */
    static final class RepositoryOnlyConfigReader extends SystemReader.Delegate {

        RepositoryOnlyConfigReader(SystemReader delegate) {
            super(delegate);
        }

        @Override
        public FileBasedConfig openUserConfig(Config parent, FS fs) {
            return emptyConfig(parent, fs);
        }

        @Override
        public FileBasedConfig openSystemConfig(Config parent, FS fs) {
            return emptyConfig(parent, fs);
        }

        @Override
        public FileBasedConfig openJGitConfig(Config parent, FS fs) {
            return emptyConfig(parent, fs);
        }

        /**
         * A configuration backed by no file at all.
         *
         * @param parent the configuration this one inherits from
         * @param fs     the file system abstraction JGit is using
         * @return a configuration that holds nothing, loads nothing and is never outdated
         */
        private static FileBasedConfig emptyConfig(Config parent, FS fs) {
            return new FileBasedConfig(parent, (File) null, fs) {

                @Override
                public void load() {
                    // Nothing to load: there is no file behind this configuration.
                }

                @Override
                public boolean isOutdated() {
                    // A file that does not exist cannot go out of date. The inherited implementation would stat it.
                    return false;
                }
            };
        }
    }
}
