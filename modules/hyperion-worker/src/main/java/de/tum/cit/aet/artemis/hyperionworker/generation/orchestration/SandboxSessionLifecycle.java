package de.tum.cit.aet.artemis.hyperionworker.generation.orchestration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Coordinates worker cancellation with artifact capture. A teardown requested during a capture is deferred until the final capture releases the session; a capture that starts
 * after teardown is skipped. Teardown runs outside the monitor, so Docker cleanup cannot hold the capture-state lock.
 */
final class SandboxSessionLifecycle {

    private static final Logger log = LoggerFactory.getLogger(SandboxSessionLifecycle.class);

    /** Observable capture and teardown states. */
    enum State {
        /** Alive, with no capture in flight and no teardown requested. */
        ACTIVE,
        /** Alive, with at least one capture holding the session. A teardown arriving now is deferred rather than run. */
        CAPTURING,
        /** A teardown arrived while a capture held the session; it runs as soon as the last capture ends. */
        DESTROY_DEFERRED,
        /** The session has been torn down. Every later capture is skipped rather than attempted. */
        DESTROYED
    }

    private final String sessionId;

    /** The actual teardown. Invoked at most once, outside this object's monitor so a slow destroy never blocks a capture from ending. */
    private final Runnable teardown;

    private int activeCaptures;

    private boolean destroyRequested;

    private boolean destroyed;

    SandboxSessionLifecycle(String sessionId, Runnable teardown) {
        this.sessionId = sessionId;
        this.teardown = teardown;
    }

    synchronized State state() {
        if (destroyed) {
            return State.DESTROYED;
        }
        if (destroyRequested) {
            return State.DESTROY_DEFERRED;
        }
        return activeCaptures > 0 ? State.CAPTURING : State.ACTIVE;
    }

    /**
     * Claims the session for a capture.
     *
     * @return {@code true} when the session is alive and the caller must pair this with {@link #endCapture()}; {@code false} when it is already destroyed, in which case the
     *         caller must skip its copy-out calls entirely
     */
    synchronized boolean beginCapture() {
        if (destroyed) {
            return false;
        }
        // A deferred destroy only exists while a capture holds the session, so admitting this one cannot resurrect a dead session; it only postpones a teardown that is already
        // waiting for work in flight. Captures are finite and run sequentially on the generation thread, so the wait is bounded by the run itself.
        activeCaptures++;
        return true;
    }

    /** Releases the session and performs a teardown that arrived while this capture held it. */
    void endCapture() {
        boolean destroyNow = false;
        synchronized (this) {
            if (activeCaptures > 0) {
                activeCaptures--;
            }
            if (destroyRequested && !destroyed && activeCaptures == 0) {
                destroyed = true;
                destroyNow = true;
            }
        }
        if (destroyNow) {
            log.debug("Performing the sandbox teardown for session {} that was deferred while its work was copied out", sessionId);
            teardown.run();
        }
    }

    /** Performs teardown synchronously if idle; otherwise records the intent without waiting for the active capture. Teardown runs at most once. */
    void requestDestroy() {
        boolean destroyNow = false;
        boolean deferred = false;
        synchronized (this) {
            if (!destroyed) {
                destroyRequested = true;
                destroyNow = activeCaptures == 0;
                deferred = !destroyNow;
                destroyed = destroyNow;
            }
        }
        if (destroyNow) {
            teardown.run();
        }
        else if (deferred) {
            log.info("Deferring the sandbox teardown for session {} until its work has been copied out", sessionId);
        }
    }

    synchronized boolean isDestroyed() {
        return destroyed;
    }
}
