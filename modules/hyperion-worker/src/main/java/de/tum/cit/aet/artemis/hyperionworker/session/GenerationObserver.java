package de.tum.cit.aet.artemis.hyperionworker.session;

import de.tum.cit.aet.artemis.hyperion.protocol.GenerationProgress;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.AgentActivitySink;

/** Worker-local observer for the existing structured instructor progress and provider accounting. */
public interface GenerationObserver extends AgentActivitySink {

    void progress(String message, GenerationProgress detail);
}
