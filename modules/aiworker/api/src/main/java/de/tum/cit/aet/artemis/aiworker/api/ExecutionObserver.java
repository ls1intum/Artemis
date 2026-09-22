package de.tum.cit.aet.artemis.aiworker.api;

import org.jspecify.annotations.Nullable;

/** Bounded progress with explicit delivery semantics; reliable updates are flushed before completion. */
@FunctionalInterface
public interface ExecutionObserver {

    void progress(String message, @Nullable String payload, boolean reliable);
}
