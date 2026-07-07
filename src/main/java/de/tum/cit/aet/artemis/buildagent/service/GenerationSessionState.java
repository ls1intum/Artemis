package de.tum.cit.aet.artemis.buildagent.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * A tiny neutral seam holding this build agent's current interactive-sandbox (Hyperion generation) session load, so admins can see it on the build-agent page next to the CI jobs
 * the sessions compete with.
 * <p>
 * The relay handler ({@link InteractiveSandboxRelayHandler}) is the only writer — it {@link #update updates} the snapshot whenever it starts or destroys a session; the agent's
 * {@link BuildAgentInformationService} is the only reader when it assembles the broadcast {@code BuildAgentInformation}. Routing the value through this bean instead of a direct
 * handler↔service dependency avoids the constructor cycle that would otherwise form (the info service would depend on the relay handler and the handler already triggers an info
 * refresh). Defaults to {@code 0 / 0}, which is exactly right for an agent that never hosts generation (cap 0): it reports zeros without touching the never-registered relay.
 */
@Lazy(false)
@Component
@Profile(PROFILE_BUILDAGENT)
public class GenerationSessionState {

    private final AtomicInteger activeSessions = new AtomicInteger();

    /** The per-agent session cap ({@code max-concurrent-generation-sessions}); {@code 0} means this agent does not host generation at all. */
    private volatile int maxSessions;

    /**
     * Overwrites the snapshot with the relay handler's authoritative post-mutation values. Called on session create/destroy (and once at startup to publish the cap).
     *
     * @param activeSessions the number of sessions this agent currently hosts
     * @param maxSessions    the configured session cap
     */
    public void update(int activeSessions, int maxSessions) {
        this.activeSessions.set(activeSessions);
        this.maxSessions = maxSessions;
    }

    public int activeSessions() {
        return activeSessions.get();
    }

    public int maxSessions() {
        return maxSessions;
    }
}
