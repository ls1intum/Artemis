package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.api.errors.CanceledException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.exception.GitException;

class GitServiceCheckoutConcurrencyTest {

    @TempDir
    Path directory;

    @Test
    void overlappingLookupsDoNotEnterTheSameCheckoutWhileItIsBeingPrepared() throws Exception {
        GitService service = spy(new GitService());
        Repository repository = mock(Repository.class);
        LocalVCRepositoryUri uri = new LocalVCRepositoryUri(URI.create("https://artemis.example.com"), "TEST", "test-template");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            calls.incrementAndGet();
            entered.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return repository;
        }).when(service).getExistingCheckedOutRepositoryByLocalPath(any(Path.class), any(LocalVCRepositoryUri.class), anyString(), anyBoolean());

        var first = new FutureTask<>(() -> service.getOrCheckoutRepository(uri, uri, directory, false, "main", false));
        var second = new FutureTask<>(() -> service.getOrCheckoutRepository(uri, uri, directory.resolve("."), false, "main", false));
        Thread firstThread = Thread.ofPlatform().start(first);
        Thread secondThread = Thread.ofPlatform().unstarted(second);
        try {
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            secondThread.start();
            // Observe the second caller inside checkout, not merely submitted to an executor.
            await().atMost(Duration.ofSeconds(5)).until(() -> second.isDone()
                    || ((secondThread.getState() == Thread.State.WAITING || secondThread.getState() == Thread.State.TIMED_WAITING) && Arrays.stream(secondThread.getStackTrace())
                            .anyMatch(frame -> frame.getClassName().equals(GitService.class.getName()) && frame.getMethodName().equals("getOrCheckoutRepository"))));
            assertThat(calls).hasValue(1);
        }
        finally {
            release.countDown();
            firstThread.join(Duration.ofSeconds(10));
            if (secondThread.getState() != Thread.State.NEW) {
                secondThread.join(Duration.ofSeconds(10));
            }
        }
        assertThat(first.get(10, TimeUnit.SECONDS)).isSameAs(repository);
        assertThat(second.get(10, TimeUnit.SECONDS)).isSameAs(repository);
        assertThat(calls).hasValue(2);
    }

    @Test
    void unrelatedPathsOnTheSameFormerStripeCanBePreparedConcurrently() throws Exception {
        GitService service = spy(new GitService());
        Repository repository = mock(Repository.class);
        LocalVCRepositoryUri uri = new LocalVCRepositoryUri(URI.create("https://artemis.example.com"), "TEST", "test-template");
        var candidates = new java.util.HashMap<Integer, Path>();
        Path[] collision = new Path[2];
        for (int index = 0; index <= 256; index++) {
            Path path = directory.resolve("repository-" + index);
            Path previous = candidates.putIfAbsent(Math.floorMod(path.hashCode(), 256), path);
            if (previous != null) {
                collision[0] = previous;
                collision[1] = path;
                break;
            }
        }
        Path firstPath = collision[0];
        Path secondPath = collision[1];
        assertThat(firstPath).isNotNull().isNotEqualTo(secondPath);
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            Path path = invocation.getArgument(0);
            (path.equals(firstPath) ? firstEntered : secondEntered).countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return repository;
        }).when(service).getExistingCheckedOutRepositoryByLocalPath(any(Path.class), any(LocalVCRepositoryUri.class), anyString(), anyBoolean());
        var first = new FutureTask<>(() -> service.getOrCheckoutRepository(uri, uri, firstPath, false, "main", false));
        var second = new FutureTask<>(() -> service.getOrCheckoutRepository(uri, uri, secondPath, false, "main", false));
        Thread firstThread = Thread.ofPlatform().start(first);
        Thread secondThread = Thread.ofPlatform().unstarted(second);
        try {
            assertThat(firstEntered.await(5, TimeUnit.SECONDS)).isTrue();
            secondThread.start();
            assertThat(secondEntered.await(5, TimeUnit.SECONDS)).as("unrelated checkout must enter while the first is still held").isTrue();
        }
        finally {
            release.countDown();
            firstThread.join(Duration.ofSeconds(10));
            if (secondThread.getState() != Thread.State.NEW) {
                secondThread.join(Duration.ofSeconds(10));
            }
        }
        assertThat(first.get(10, TimeUnit.SECONDS)).isSameAs(repository);
        assertThat(second.get(10, TimeUnit.SECONDS)).isSameAs(repository);
    }

    @Test
    void aFailedPreparationReleasesTheCheckoutForAnotherThread() throws Exception {
        GitService service = spy(new GitService());
        Repository repository = mock(Repository.class);
        LocalVCRepositoryUri uri = new LocalVCRepositoryUri(URI.create("https://artemis.example.com"), "TEST", "test-template");
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            if (calls.getAndIncrement() == 0) {
                throw new GitException("Preparation failed");
            }
            return repository;
        }).when(service).getExistingCheckedOutRepositoryByLocalPath(any(Path.class), any(LocalVCRepositoryUri.class), anyString(), anyBoolean());
        assertThatExceptionOfType(GitException.class).isThrownBy(() -> service.getOrCheckoutRepository(uri, uri, directory, false, "main", false));

        var retry = new FutureTask<>(() -> service.getOrCheckoutRepository(uri, uri, directory, false, "main", false));
        Thread thread = Thread.ofPlatform().start(retry);
        try {
            assertThat(retry.get(10, TimeUnit.SECONDS)).isSameAs(repository);
        }
        finally {
            thread.interrupt();
            thread.join(Duration.ofSeconds(10));
        }
    }

    @Test
    void anInterruptedLookupDoesNotStartPreparingTheCheckout() {
        GitService service = spy(new GitService());
        LocalVCRepositoryUri uri = new LocalVCRepositoryUri(URI.create("https://artemis.example.com"), "TEST", "test-template");
        Thread.currentThread().interrupt();
        try {
            assertThatExceptionOfType(CanceledException.class).isThrownBy(() -> service.getOrCheckoutRepository(uri, uri, directory, false, "main", false));
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        }
        finally {
            Thread.interrupted();
        }
    }

}
