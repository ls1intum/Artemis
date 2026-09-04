package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serialises sandbox teardown against artifact capture, so a cancellation cannot destroy work that is mid-copy.
 * <p>
 * One instance guards one sandbox session, and it is the single teardown route for that session: the node-local cancel hook, the generation thread's terminal
 * {@code destroyQuietly}, and {@link GenerationOutcome#close()} all arrive here. The two sides run on different pools — the hook is dispatched on
 * {@code GenerationJobService}'s cancellation executor while every capture runs on the generation thread — and nothing else orders them, so without this gate a cancellation
 * arriving mid-capture destroys the container and every remaining {@code copyOut} fails with {@code Remote sandbox operation COPY_OUT failed}.
 * <p>
 * The gate is deliberately asymmetric:
 * <ul>
 * <li>{@link #requestDestroy()} never blocks. It runs on a shared executor that also carries unrelated cancellations, so waiting for a multi-second repository copy there would
 * stall them. A destroy that cannot run immediately is recorded as intent and performed by the capture that is holding the session, exactly once.</li>
 * <li>{@link #beginCapture()} returns {@code false} once the session is destroyed. The caller must then skip its {@code copyOut} calls rather than issue calls that are
 * guaranteed to throw — that is what keeps the "Could not extract … files" warnings out of the log, and it is the point at which a caller falls back to a snapshot taken
 * earlier.</li>
 * </ul>
 * A capture that never ends defers the destroy forever. There is no timer here on purpose: a hung relay leaves a container whose activity stamp stops advancing, and
 * {@code InteractiveSandboxReaperService} removes it on its next idle sweep. That reaper runs on every build agent (including one that hosts no sandboxes), reaps by inactivity
 * rather than age, and refuses to reap a session with an operation still in flight — so it is the correct backstop and a timer here could only race it.
 */
final class SandboxSessionLifecycle {

    private static final Logger log = LoggerFactory.getLogger(SandboxSessionLifecycle.class);

    /** The observable states of one session; {@link #DESTROY_DEFERRED} is the state the observed incident produced and this type exists to make safe. */
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

    /** Destroys the session now, or records the intent and returns when a capture is holding it. Never blocks, and destroys at most once however many callers race here. */
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
