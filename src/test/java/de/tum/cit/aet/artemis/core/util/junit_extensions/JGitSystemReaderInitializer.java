package de.tum.cit.aet.artemis.core.util.junit_extensions;

import org.eclipse.jgit.util.SystemReader;

import de.tum.cit.aet.artemis.core.config.JGitConfig;

/**
 * Installs, before the test plan starts, the same {@link SystemReader} the server installs at startup.
 * <p>
 * Skipping the system-level git config is needed here for its own reason: a system gitconfig such as
 * {@code /opt/homebrew/etc/gitconfig} can exceed JGit's default 5 MB file size limit, which makes every git operation
 * fail with "File is too large". {@link JGitConfig} skips it, along with the user and jgit configs, so a test run and a
 * server behave the same way.
 * <p>
 * The reader must be installed <b>exactly once per JVM</b>. {@link SystemReader#setInstance(SystemReader)} first resets
 * JGit's static platform detection caches ({@code isWindows}, {@code isMacOS}, {@code isLinux}) to {@code null} and only
 * afterwards re-derives them via {@code init() -> setPlatformChecker()}. Since {@code SystemReader#isWindows()} re-reads
 * the static field after assigning it, a concurrent {@code setInstance(...)} nulls the field between the assignment and
 * the read, which throws {@code NullPointerException: Cannot invoke "java.lang.Boolean.booleanValue()" because
 * "org.eclipse.jgit.util.SystemReader.isWindows" is null}. Calling {@code setInstance} from {@code @BeforeAll} of a base
 * test class means one call per test class, and test classes run in parallel, so those calls collide with each other and
 * with git operations of tests that are already running.
 * <p>
 * {@link #configureOnce()} is therefore idempotent, and {@link GlobalCleanupListener#testPlanExecutionStarted} calls it
 * before the test plan starts executing, i.e. while the JVM is still single-threaded. Every Spring context that starts
 * later finds it already installed and does nothing.
 */
public final class JGitSystemReaderInitializer {

    private JGitSystemReaderInitializer() {
    }

    /**
     * Whether the custom {@link SystemReader} has already been installed in this JVM.
     * Tests assert this to detect the regression where the up-front installation is dropped and the first
     * {@link SystemReader#setInstance(SystemReader)} call happens lazily, while other test classes already run git operations.
     *
     * @return true once {@link #configureOnce()} has installed the reader
     */
    public static boolean isConfigured() {
        return JGitConfig.isSystemReaderConfigured();
    }

    /**
     * Installs the custom {@link SystemReader} the first time it is called and does nothing on every subsequent call.
     */
    public static void configureOnce() {
        JGitConfig.configureSystemReaderOnce();
    }
}
