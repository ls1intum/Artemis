package de.tum.cit.aet.artemis.localvc.service.ssh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.sshd.common.util.threads.CloseableExecutorService;
import org.apache.sshd.common.util.threads.SshThreadPoolExecutor;
import org.apache.sshd.common.util.threads.SshdThreadFactory;
import org.apache.sshd.common.util.threads.ThreadUtils;
import org.junit.jupiter.api.Test;

/**
 * Documents how the pool of the git commands has to be handed out. A command shuts its executor down when it ends. A wrapper that ignores the shutdown but is shared by all
 * commands is closed for good by the first command, and every later command fails, so each command needs a wrapper of its own around the shared pool.
 */
class SshCommandExecutorTest {

    private SshThreadPoolExecutor newPool() {
        return new SshThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(2), new SshdThreadFactory("git-ssh-test"), (task, executor) -> {
            throw new RejectedExecutionException("full");
        });
    }

    @Test
    void aWrapperPerCommandKeepsThePoolUsableAfterACommandEnded() throws Exception {
        SshThreadPoolExecutor pool = newPool();
        try {
            CloseableExecutorService first = ThreadUtils.noClose(pool);
            assertThat(first.submit(() -> "first").get(5, TimeUnit.SECONDS)).isEqualTo("first");
            // the end of the first command
            first.shutdownNow();

            CloseableExecutorService second = ThreadUtils.noClose(pool);
            assertThat(second.submit(() -> "second").get(5, TimeUnit.SECONDS)).isEqualTo("second");
        }
        finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aSharedWrapperRefusesEveryCommandAfterTheFirstOneEnded() {
        SshThreadPoolExecutor pool = newPool();
        try {
            CloseableExecutorService shared = ThreadUtils.noClose(pool);
            shared.shutdownNow();

            assertThatThrownBy(() -> shared.submit(() -> "later")).isInstanceOf(IllegalStateException.class);
        }
        finally {
            pool.shutdownNow();
        }
    }
}
