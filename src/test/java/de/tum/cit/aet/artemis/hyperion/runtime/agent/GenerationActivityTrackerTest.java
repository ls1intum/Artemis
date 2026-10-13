package de.tum.cit.aet.artemis.hyperion.runtime.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.hyperion.protocol.GenerationActivity;

class GenerationActivityTrackerTest {

    @Test
    void idleRunHasNoInventedActivity() {
        assertThat(new GenerationActivityTracker().snapshot()).isNull();
    }

    @Test
    void waitingForTheFirstModelCallStartsActivityWithoutInventingCompletedWork() {
        var tracker = new GenerationActivityTracker();

        assertThat(tracker.waitingOnModel()).isEqualTo(new GenerationActivity(null, 0, 0, true, 0, 0, 0));
        assertThat(tracker.snapshot()).isEqualTo(new GenerationActivity(null, 0, 0, false, 0, 0, 0));
    }

    @Test
    void attemptsResetTurnsButKeepRunTotalsAndSnapshotsImmutable() {
        var tracker = new GenerationActivityTracker();
        tracker.step(GenerationActivityTracker.CONCEPT_STEP);
        tracker.attempt(1);
        tracker.turn(3);
        tracker.recordModelCall();
        tracker.recordToolCall();
        tracker.recordFileWritten();
        GenerationActivity first = tracker.waitingOnModel();

        tracker.attempt(2);
        tracker.step(null);
        tracker.recordModelCall();

        assertThat(first).isEqualTo(new GenerationActivity("concept", 1, 3, true, 1, 1, 1));
        assertThat(tracker.snapshot()).isEqualTo(new GenerationActivity(null, 2, 0, false, 2, 1, 1));
        assertThat(new GenerationActivityTracker().snapshot()).isNull();
    }

    @Test
    void recordingWorkStartsActivityWithoutRequiringAnAuthoringStage() {
        var tracker = new GenerationActivityTracker();
        tracker.recordToolCall();
        tracker.recordFileWritten();

        assertThat(tracker.snapshot()).isEqualTo(new GenerationActivity(null, 0, 0, false, 0, 1, 1));
    }

    @Test
    void sinkAttachesActivityOnlyAfterTheRunStarts() {
        var tracker = new GenerationActivityTracker();
        List<String> messages = new ArrayList<>();
        List<GenerationActivity> activities = new ArrayList<>();
        AgentActivitySink sink = new AgentActivitySink() {

            @Override
            public void accept(String message) {
                messages.add(message);
            }

            @Override
            public GenerationActivityTracker activityTracker() {
                return tracker;
            }

            @Override
            public void activity(String message, GenerationActivity activity) {
                messages.add(message);
                activities.add(activity);
            }
        };

        AgentActivitySink.emit(sink, "Preparing");
        assertThat(activities).isEmpty();
        tracker.turn(1);
        AgentActivitySink.emit(sink, "Authoring");

        assertThat(messages).containsExactly("Preparing", "Authoring");
        assertThat(activities).containsExactly(new GenerationActivity(null, 0, 1, false, 0, 0, 0));
        assertThat(AgentActivitySink.trackerOf(sink)).isSameAs(tracker);
    }

    @Test
    void textOnlySinksRemainCompatibleWithActivityEvents() {
        List<String> messages = new ArrayList<>();
        AgentActivitySink sink = messages::add;
        AgentActivitySink.emit(sink, "Preparing");
        sink.activity("Authoring", new GenerationActivity(null, 1, 1, false, 0, 0, 0));
        AgentActivitySink.emit(messages::add, "Reviewing");
        AgentActivitySink.emit(null, "Discarded");

        assertThat(messages).containsExactly("Preparing", "Authoring", "Reviewing");
        assertThat(sink.activityTracker()).isNull();
        assertThat(AgentActivitySink.trackerOf(messages::add)).isNull();
        assertThat(AgentActivitySink.trackerOf(null)).isNull();
    }
}
