package de.tum.cit.aet.artemis.iris.service.pyris.job;

/**
 * A Pyris job that has a session id and stored its own LLM usage tracing ID.
 * This is used for chat jobs where we need to reference the trace ID later after chat suggestions have been generated.
 */
public interface TrackedSessionBasedPyrisJob extends PyrisJob {

    long sessionId();

    Long traceId();

    TrackedSessionBasedPyrisJob withTraceId(long traceId);
}
