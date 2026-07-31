package de.tum.cit.aet.artemis.core.util.junit_extensions;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.util.SystemReader;
import org.junit.jupiter.api.Test;

/**
 * Guards the two invariants that keep JGit's {@link SystemReader} from breaking parallel tests.
 * <p>
 * {@link SystemReader#setInstance(SystemReader)} nulls JGit's static platform detection caches before re-deriving them. Calling it while other threads run git
 * operations makes {@code SystemReader#isWindows()} throw a {@link NullPointerException}, which is what happened when the reader was installed from a
 * {@code @BeforeAll} of the integration test base class (once per test class, with test classes running in parallel).
 * <p>
 * The race therefore has two ingredients, and each is covered by one invariant:
 * <ol>
 * <li>more than one {@code setInstance} call: prevented by the guard in {@link JGitSystemReaderInitializer#configureOnce()},</li>
 * <li>a {@code setInstance} call concurrent with git work: prevented by installing the reader in
 * {@link GlobalCleanupListener#testPlanExecutionStarted} before any test runs.</li>
 * </ol>
 * Note that the second ingredient cannot be reproduced from within a test: every test already runs inside the test plan, so by then the reader is installed and
 * {@code configureOnce()} is a no-op. It is asserted structurally instead, via {@link JGitSystemReaderInitializer#isConfigured()}, plus the ArchUnit rule
 * {@code ArchitectureTest#testNoJGitSystemReaderConfigurationOutsideInitializer}.
 */
class JGitSystemReaderInitializerTest {

    private static final int THREAD_COUNT = 16;

    @Test
    void shouldBeConfiguredBeforeAnyTestRuns() {
        assertThat(JGitSystemReaderInitializer.isConfigured())
                .as("the SystemReader must already be installed before the first test runs, otherwise the first installation resets JGit's platform caches "
                        + "while other test classes are already executing git operations")
                .isTrue();
    }

    @Test
    void shouldInstallSystemReaderOnlyOnce() throws Exception {
        JGitSystemReaderInitializer.configureOnce();
        SystemReader installedReader = SystemReader.getInstance();

        runConcurrently(JGitSystemReaderInitializer::configureOnce);

        assertThat(SystemReader.getInstance()).as("repeated configuration must not replace the installed SystemReader").isSameAs(installedReader);
    }

    /**
     * Exercises the idempotent path that every test class hits: concurrent {@code configureOnce()} calls must stay no-ops and must not disturb git platform
     * detection running in parallel.
     */
    @Test
    void shouldKeepPlatformDetectionUsableWhileConfiguringConcurrently() throws Exception {
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        runConcurrently(() -> {
            try {
                JGitSystemReaderInitializer.configureOnce();
                for (int i = 0; i < 500; i++) {
                    SystemReader reader = SystemReader.getInstance();
                    reader.isWindows();
                    reader.isMacOS();
                    reader.isLinux();
                }
            }
            catch (Throwable throwable) {
                failures.add(throwable);
            }
        });

        assertThat(failures).as("JGit platform detection must not fail while the SystemReader is configured").isEmpty();
    }

    private static void runConcurrently(Runnable action) throws InterruptedException {
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(THREAD_COUNT);
        List<Thread> threads = new ArrayList<>(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            Thread thread = new Thread(() -> {
                try {
                    startSignal.await();
                    action.run();
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                finally {
                    doneSignal.countDown();
                }
            });
            threads.add(thread);
            thread.start();
        }

        startSignal.countDown();
        assertThat(doneSignal.await(30, TimeUnit.SECONDS)).as("all threads should finish").isTrue();
        for (Thread thread : threads) {
            thread.join();
        }
    }
}
