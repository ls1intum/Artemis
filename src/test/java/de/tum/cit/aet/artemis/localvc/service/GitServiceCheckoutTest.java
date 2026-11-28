package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.file.GC;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.test.util.ReflectionTestUtils;

@Isolated("Temporarily replaces JGit's garbage collection executor")
class GitServiceCheckoutTest {

    @TempDir
    Path tempDir;

    @Test
    void getOrCheckoutRepositoryWithLocalPath_freshCheckoutCanBeCopied() throws Exception {
        var repositoryUri = new LocalVCRepositoryUri(URI.create("https://artemis.example.com"), "ABC", "abc-student");
        Git.init().setBare(true).setInitialBranch("main").setDirectory(repositoryUri.getLocalRepositoryPath(tempDir).toFile()).call().close();
        var gitService = new GitService();
        gitService.defaultBranch = "main";
        ReflectionTestUtils.setField(gitService, "localVCBasePath", tempDir);
        Path checkoutDir = tempDir.resolve("checkout");
        Path exportDir = tempDir.resolve("export");
        List<Runnable> pendingGc = new ArrayList<>();
        var executor = mock(ExecutorService.class);
        doAnswer(invocation -> {
            pendingGc.add(invocation.getArgument(0));
            return null;
        }).when(executor).execute(any(Runnable.class));

        GC.setExecutor(executor);
        try (var checkout = gitService.getOrCheckoutRepositoryWithLocalPath(repositoryUri, checkoutDir, true, false)) {
            FileUtils.copyDirectory(checkout.getLocalPath().toFile(), exportDir.toFile(), entry -> {
                // Let JGit unlink the lock after the copy has listed it, but before it is copied.
                if (entry.getName().equals("gc.log.lock")) {
                    pendingGc.forEach(Runnable::run);
                    pendingGc.clear();
                }
                return true;
            });
            assertThat(exportDir.resolve(".git/HEAD")).isRegularFile();
        }
        finally {
            GC.setExecutor(null);
            pendingGc.forEach(Runnable::run);
        }
    }
}
