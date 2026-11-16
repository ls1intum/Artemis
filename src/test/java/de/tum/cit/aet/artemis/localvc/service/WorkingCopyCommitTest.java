package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.programming.domain.Repository;

/**
 * Unit test that staging, checking and committing of the online editor happen while the lock of the working copy is held.
 */
class WorkingCopyCommitTest {

    @TempDir
    Path workingTree;

    @Test
    void anotherOperationOnTheWorkingCopyWaitsUntilTheCommitIsDone() throws Exception {
        Git.init().setDirectory(workingTree.toFile()).setInitialBranch("main").call().close();
        GitService gitService = mock(GitService.class);
        AtomicBoolean competitorRanDuringCommit = new AtomicBoolean();
        AtomicBoolean commitDone = new AtomicBoolean();
        AtomicReference<CompletableFuture<Void>> competitorFuture = new AtomicReference<>();
        try (Repository repository = AbstractGitService.linkRepositoryForExistingGit(workingTree,
                new LocalVCRepositoryUri(URI.create("https://artemis.example.com"), "ABC", "abc-exercise"), "main", false, true)) {
            when(gitService.commitAndPush(any(), anyString(), anyBoolean(), any())).thenAnswer(invocation -> {
                // another request for the same working copy, for example a second commit of the editor
                CompletableFuture<Void> competitor = CompletableFuture.runAsync(() -> {
                    try {
                        WorkingCopyLocks.shared().withLock(AbstractGitService.workingCopyPath(repository), () -> {
                            competitorRanDuringCommit.set(!commitDone.get());
                            return null;
                        });
                    }
                    catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                });
                competitorFuture.set(competitor);
                // the competitor gets time to run, which it must not use
                try {
                    competitor.get(300, TimeUnit.MILLISECONDS);
                }
                catch (TimeoutException expected) {
                    // it waits for the lock
                }
                commitDone.set(true);
                return "commit-id";
            });

            String commit = WorkingCopyCommit.commitChanges(gitService, repository, Long.MAX_VALUE, "message", new User());

            assertThat(commit).isEqualTo("commit-id");
        }
        // after the commit the competitor gets the lock
        competitorFuture.get().get(5, TimeUnit.SECONDS);
        assertThat(competitorRanDuringCommit).isFalse();
    }

    @Test
    void aPullOfTheWorkingCopyWaitsUntilTheCommitIsDone() throws Exception {
        Git.init().setDirectory(workingTree.toFile()).setInitialBranch("main").call().close();
        GitService committingGitService = mock(GitService.class);
        GitService pullingGitService = new GitService();
        AtomicBoolean commitDone = new AtomicBoolean();
        AtomicBoolean pullFinishedDuringCommit = new AtomicBoolean();
        AtomicReference<CompletableFuture<Void>> pull = new AtomicReference<>();
        try (Repository repository = AbstractGitService.linkRepositoryForExistingGit(workingTree,
                new LocalVCRepositoryUri(URI.create("https://artemis.example.com"), "ABC", "abc-exercise"), "main", false, true)) {
            when(committingGitService.commitAndPush(any(), anyString(), anyBoolean(), any())).thenAnswer(invocation -> {
                // the pull of the editor on the same working copy, started between the check and the commit
                pull.set(CompletableFuture.runAsync(() -> {
                    pullingGitService.pullIgnoreConflicts(repository);
                    pullFinishedDuringCommit.compareAndSet(false, !commitDone.get());
                }));
                try {
                    pull.get().get(300, TimeUnit.MILLISECONDS);
                }
                catch (TimeoutException expected) {
                    // the pull waits for the lock
                }
                commitDone.set(true);
                return "commit-id";
            });

            WorkingCopyCommit.commitChanges(committingGitService, repository, Long.MAX_VALUE, "message", new User());
            pull.get().get(10, TimeUnit.SECONDS);
        }

        assertThat(pullFinishedDuringCommit).as("the pull must not change the index while the commit stages, checks and commits").isFalse();
    }
}
