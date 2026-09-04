package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationActivityDTO;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentActivitySink;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.GenerationActivityTracker;

/**
 * Gives a nested agent loop the run's activity channel while deliberately dropping its text channel.
 * <p>
 * Some agent loops run <em>inside</em> a stage that owns its own instructor-facing narration — concept discovery is the case this exists for. Their model calls are ordinary
 * provider calls that can block for minutes, so the run must keep saying it is waiting on the model and keep counting those calls. Their prose, however, describes the generic
 * authoring loop rather than the stage the instructor is being shown: a one-turn text session always ends without tool calls, so forwarding the text channel would announce
 * "Preparing the exercise for verification." while the run is still inventing an exercise idea, and the empty-response and step-limit lines would read as failures of a stage
 * that is progressing normally. The enclosing stage already narrates itself ("Exploring exercise concepts", "Reviewing exercise concepts", "Selected concept …") and already
 * reports the loop's outcome, so dropping the text is a loss of nothing and a removal of contradictions.
 * <p>
 * Concretely: only the pre-call waiting marker is forwarded, because it is the one emission whose meaning does not depend on which stage produced it. Everything else — plain
 * {@link #accept(String)} lines and any activity event that is not a waiting marker — is discarded. The shared {@link GenerationActivityTracker} is handed through unchanged, so
 * the turn number and the model/tool/file counters keep advancing on the run's own tracker whether or not an emission is forwarded.
 */
final class ModelWaitProgressSink implements GenerationProgressSink {

    private final AgentActivitySink delegate;

    private final GenerationActivityTracker tracker;

    private ModelWaitProgressSink(AgentActivitySink delegate, GenerationActivityTracker tracker) {
        this.delegate = delegate;
        this.tracker = tracker;
    }

    /**
     * Wraps a run's progress sink for use as a nested loop's step listener.
     *
     * @param progress the run's progress sink
     * @return the wrapper, or {@code null} when {@code progress} carries no activity tracker — a text-only consumer has no structured channel to receive the waiting marker on,
     *         and the text channel is exactly what must not be forwarded, so such a loop stays silent as before
     */
    @Nullable
    static Consumer<String> wrap(@Nullable Consumer<String> progress) {
        GenerationActivityTracker tracker = AgentActivitySink.trackerOf(progress);
        return tracker == null ? null : new ModelWaitProgressSink((AgentActivitySink) progress, tracker);
    }

    @Override
    public void accept(String message) {
        // Dropped on purpose; see the class javadoc. The enclosing stage narrates this loop.
    }

    @Override
    public void activity(String message, ExerciseGenerationActivityDTO activity) {
        if (activity.waitingOnModel()) {
            delegate.activity(message, activity);
        }
    }

    @Override
    public GenerationActivityTracker activityTracker() {
        return tracker;
    }
}
