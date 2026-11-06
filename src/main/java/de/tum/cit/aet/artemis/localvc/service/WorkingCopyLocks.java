package de.tum.cit.aet.artemis.localvc.service;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.jgit.api.errors.CanceledException;
import org.eclipse.jgit.api.errors.GitAPIException;

/**
 * Serializes checkout, pull, clone and retained deletion operations for each normalized working-copy path on this node.
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
        ReentrantLock lock;
        try {
            lock = acquire(path);
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

    /**
     * Runs filesystem cleanup while owning the same working-copy lock used by checkout.
     *
     * @param path      the working-copy path
     * @param operation the cleanup operation; it must not acquire another working-copy lock
     * @return the result of cleanup
     * @throws IOException if cleanup fails or waiting for the working copy is interrupted
     */
    <T> T withIOLock(Path path, IOOperation<T> operation) throws IOException {
        ReentrantLock lock;
        try {
            lock = acquire(path);
        }
        catch (InterruptedException exception) {
            // As for checkout, do not leave the worker interrupted for its next JGit operation.
            var cancellation = new InterruptedIOException("Waiting for a working copy got interrupted.");
            cancellation.initCause(exception);
            throw cancellation;
        }
        try {
            return operation.run();
        }
        finally {
            lock.unlock();
        }
    }

    private ReentrantLock acquire(Path path) throws InterruptedException {
        ReentrantLock lock = locks[Math.floorMod(path.toAbsolutePath().normalize().hashCode(), locks.length)];
        lock.lockInterruptibly();
        return lock;
    }

    @FunctionalInterface
    interface IOOperation<T> {

        T run() throws IOException;
    }

    @FunctionalInterface
    interface CheckoutOperation<T> {

        T run() throws GitAPIException;
    }
}
