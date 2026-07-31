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
 * Guards the invariant that JGit's {@link SystemReader} is installed exactly once per JVM.
 * <p>
 * {@link SystemReader#setInstance(SystemReader)} nulls JGit's static platform detection caches before re-deriving them. Calling it while other threads run git
 * operations makes {@code SystemReader#isWindows()} throw a {@link NullPointerException}, which is what happened when the reader was installed from a
 * {@code @BeforeAll} of the integration test base class (once per test class, with test classes running in parallel).
 */
class JGitSystemReaderInitializerTest {

    private static final int THREAD_COUNT = 16;

    @Test
    void shouldInstallSystemReaderOnlyOnce() throws Exception {
        JGitSystemReaderInitializer.configureOnce();
        SystemReader installedReader = SystemReader.getInstance();

        runConcurrently(JGitSystemReaderInitializer::configureOnce);

        assertThat(SystemReader.getInstance()).as("repeated configuration must not replace the installed SystemReader").isSameAs(installedReader);
    }

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
