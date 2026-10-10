package de.tum.cit.aet.artemis.localvc.service;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.jgit.api.errors.CanceledException;

import de.tum.cit.aet.artemis.programming.exception.GitException;

/** Serializes each working copy without making unrelated repositories share a lock. */
final class RepositoryCheckoutLocks {

    private final ConcurrentHashMap<Path, Entry> entries = new ConcurrentHashMap<>();

    private static final class Entry {

        private final ReentrantLock lock = new ReentrantLock();

        private int references;
    }

    Lease acquire(Path path, int timeoutSeconds) throws CanceledException {
        Path key = path.toAbsolutePath().normalize();
        Entry entry = entries.compute(key, (_, current) -> {
            Entry retained = current == null ? new Entry() : current;
            retained.references++;
            return retained;
        });
        boolean acquired = false;
        try {
            acquired = entry.lock.tryLock(timeoutSeconds, TimeUnit.SECONDS);
            if (!acquired) {
                throw new GitException("The local repository is still being prepared");
            }
            return new Lease(key, entry);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CanceledException("Waiting for the local repository got interrupted");
        }
        finally {
            if (!acquired) {
                releaseReference(key, entry);
            }
        }
    }

    private void releaseReference(Path key, Entry entry) {
        // Waiters retain the entry before blocking, so removing the last reference cannot create a second live lock for this path.
        entries.compute(key, (_, current) -> --entry.references == 0 ? null : current);
    }

    final class Lease implements AutoCloseable {

        private final Path key;

        private final Entry entry;

        private boolean closed;

        private Lease(Path key, Entry entry) {
            this.key = key;
            this.entry = entry;
        }

        @Override
        public void close() {
            if (!closed) {
                entry.lock.unlock();
                releaseReference(key, entry);
                closed = true;
            }
        }
    }
}
