package de.tum.cit.aet.artemis.localvc.service;

import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.jgit.api.errors.CanceledException;
import org.eclipse.jgit.api.errors.GitAPIException;

/**
 * Serializes checkout, pull and clone operations for each normalized working-copy path on this node.
 * Fixed lock striping bounds memory use; unrelated paths sharing a stripe only wait for each other.
 */
final class WorkingCopyLocks {

    private final ReentrantLock[] locks = new ReentrantLock[128];

    WorkingCopyLocks() {
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new ReentrantLock();
        }
    }

    /**
     * Runs a checkout operation while owning its working-copy lock, releasing it on success and failure.
     *
     * @param path      the working-copy path
     * @param operation the checkout operation; it must not acquire another working-copy lock
     * @return the result of the checkout
     * @throws GitAPIException if checkout fails
     */
    <T> T withLock(Path path, CheckoutOperation<T> operation) throws GitAPIException {
        ReentrantLock lock = locks[Math.floorMod(path.toAbsolutePath().normalize().hashCode(), locks.length)];
        try {
            lock.lockInterruptibly();
        }
        catch (InterruptedException exception) {
            // Do not leave a ForkJoin worker interrupted: its next JGit checkout could otherwise fail in interruptible NIO and delete the working copy.
            throw new CanceledException("Waiting for a working copy got interrupted.");
        }
        try {
            return operation.run();
        }
        finally {
            lock.unlock();
        }
    }

    @FunctionalInterface
    interface CheckoutOperation<T> {

        T run() throws GitAPIException;
    }
}
