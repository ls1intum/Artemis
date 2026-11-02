package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.file.GC;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.core.service.TempFileUtilService;

@Isolated("Temporarily replaces JGit's shared garbage collection executor")
class WorkingCopyLifecycleTest {

    @TempDir
    Path tempDir;

    @Test
    void workingCopyFinishesGarbageCollectionBeforeItCanBeDeleted() throws Exception {
        var service = new LocalVCLocalCITestService();
        ReflectionTestUtils.setField(service, "localVCBasePath", tempDir);
        ReflectionTestUtils.setField(service, "tempPath", tempDir);
        ReflectionTestUtils.setField(service, "tempFileUtilService", new TempFileUtilService(tempDir));
        service.defaultBranch = "main";
        Path barePath = tempDir.resolve("PROJECT/repository.git");
        var executor = new DeferredGcExecutor();

        try (Git bare = Git.init().setBare(true).setInitialBranch("main").setDirectory(barePath.toFile()).call()) {
            GC.setExecutor(executor);
            try {
                var repository = service.cloneWorkingCopy("PROJECT", "repository");
                try {
                    // Clone itself fetches. Checking before another fetch catches configuring GC too late, after clone returned.
                    assertThat(executor.pending).as("clone must not leave garbage collection running in the scratch directory").isEmpty();
                    repository.workingCopy().fetch().call();
                    assertThat(executor.pending).as("later fetches must also finish garbage collection before returning").isEmpty();
                    assertThat(repository.workingCopyPath().resolve(".git/gc.log.lock")).doesNotExist();
                }
                finally {
                    // Drain even on an assertion failure, so the regression's RED run leaves no work behind.
                    executor.drain();
                    repository.deleteWorkingCopy();
                }
                assertThat(repository.workingCopyPath()).doesNotExist();
                assertThat(barePath).isDirectory();
            }
            finally {
                executor.drain();
                GC.setExecutor(null);
            }
        }
    }

    /** Holds background GC at the exact boundary that otherwise races directory deletion. */
    private static final class DeferredGcExecutor extends AbstractExecutorService {

        private final List<Runnable> pending = new ArrayList<>();

        @Override
        public void execute(Runnable command) {
            pending.add(command);
        }

        private void drain() {
            List.copyOf(pending).forEach(Runnable::run);
            pending.clear();
        }

        @Override
        public void shutdown() {
        }

        @Override
        public List<Runnable> shutdownNow() {
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    }
}
