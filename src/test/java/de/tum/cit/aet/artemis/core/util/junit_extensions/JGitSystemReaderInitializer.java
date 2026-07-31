package de.tum.cit.aet.artemis.core.util.junit_extensions;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;

/**
 * Installs a {@link SystemReader} that makes JGit skip system-level git config files.
 * This is necessary because system gitconfig files (e.g. {@code /opt/homebrew/etc/gitconfig}) can exceed JGit's default 5 MB file size limit,
 * which makes every git operation fail with "File is too large".
 * <p>
 * The reader must be installed <b>exactly once per JVM</b>. {@link SystemReader#setInstance(SystemReader)} first resets JGit's static platform detection caches
 * ({@code isWindows}, {@code isMacOS}, {@code isLinux}) to {@code null} and only afterwards re-derives them via {@code init() -> setPlatformChecker()}.
 * Since {@code SystemReader#isWindows()} re-reads the static field after assigning it, a concurrent {@code setInstance(...)} nulls the field between the
 * assignment and the read, which throws {@code NullPointerException: Cannot invoke "java.lang.Boolean.booleanValue()" because
 * "org.eclipse.jgit.util.SystemReader.isWindows" is null}. Calling {@code setInstance} from {@code @BeforeAll} of a base test class means one call per test
 * class, and test classes run in parallel, so those calls collide with each other and with git operations of tests that are already running.
 * <p>
 * {@link #configureOnce()} is therefore idempotent, and {@link GlobalCleanupListener#testPlanExecutionStarted} calls it before the test plan starts executing,
 * i.e. while the JVM is still single-threaded.
 */
public final class JGitSystemReaderInitializer {

    private static final AtomicBoolean CONFIGURED = new AtomicBoolean(false);

    private JGitSystemReaderInitializer() {
    }

    /**
     * Installs the custom {@link SystemReader} the first time it is called and does nothing on every subsequent call.
     */
    public static void configureOnce() {
        if (!CONFIGURED.compareAndSet(false, true)) {
            return;
        }
        final SystemReader defaultReader = SystemReader.getInstance();

        SystemReader.setInstance(new SystemReader() {

            @Override
            public String getHostname() {
                return defaultReader.getHostname();
            }

            @Override
            public String getenv(String variable) {
                return defaultReader.getenv(variable);
            }

            @Override
            public String getProperty(String key) {
                return defaultReader.getProperty(key);
            }

            @Override
            public FileBasedConfig openUserConfig(Config parent, FS fs) {
                return defaultReader.openUserConfig(parent, fs);
            }

            @Override
            public FileBasedConfig openSystemConfig(Config parent, FS fs) {
                // Return an empty config instead of reading the potentially large system gitconfig
                return new FileBasedConfig(parent, null, fs) {

                    @Override
                    public void load() {
                        // Don't load anything - skip system config
                    }

                    @Override
                    public boolean isOutdated() {
                        return false;
                    }
                };
            }

            @Override
            public FileBasedConfig openJGitConfig(Config parent, FS fs) {
                return defaultReader.openJGitConfig(parent, fs);
            }

            @Override
            public Instant now() {
                return defaultReader.now();
            }

            @Override
            public ZoneOffset getTimeZoneAt(Instant when) {
                return defaultReader.getTimeZoneAt(when);
            }

            @SuppressWarnings("deprecation")
            @Override
            public long getCurrentTime() {
                return defaultReader.getCurrentTime();
            }

            @SuppressWarnings("deprecation")
            @Override
            public int getTimezone(long when) {
                return defaultReader.getTimezone(when);
            }
        });
    }
}
