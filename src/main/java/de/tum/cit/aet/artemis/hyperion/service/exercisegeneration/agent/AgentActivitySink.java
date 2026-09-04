package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationActivityDTO;

/**
 * Progress sink that optionally attaches the run's live activity to a progress line. Text-only consumers receive only the message.
 * <p>
 * It lives in this package rather than beside the orchestration sink it is mixed into because the agent loop is the layer that produces turn-level activity, and this package
 * deliberately has no dependency on the orchestration package.
 */
public interface AgentActivitySink extends Consumer<String> {

    default void activity(String message, ExerciseGenerationActivityDTO activity) {
        accept(message);
    }

    /**
     * The tracker this run's activity is recorded into.
     *
     * @return the tracker, or {@code null} for a sink that does not track activity
     */
    @Nullable
    default GenerationActivityTracker activityTracker() {
        return null;
    }

    /**
     * The tracker behind an arbitrary progress consumer.
     *
     * @param sink the progress consumer, which may be {@code null} or a text-only lambda
     * @return the tracker, or {@code null} when there is none to record into
     */
    @Nullable
    static GenerationActivityTracker trackerOf(@Nullable Consumer<String> sink) {
        return sink instanceof AgentActivitySink activitySink ? activitySink.activityTracker() : null;
    }

    /**
     * Emits one progress line with the run's current activity attached where the sink tracks it, and as plain text everywhere else.
     *
     * @param sink    the progress consumer; {@code null} discards the line
     * @param message the human-readable progress line
     */
    static void emit(@Nullable Consumer<String> sink, String message) {
        if (sink == null) {
            return;
        }
        GenerationActivityTracker tracker = trackerOf(sink);
        ExerciseGenerationActivityDTO activity = tracker == null ? null : tracker.snapshot();
        if (activity == null) {
            sink.accept(message);
            return;
        }
        ((AgentActivitySink) sink).activity(message, activity);
    }
}
