package de.tum.cit.aet.artemis.lti.nightly;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.lti.AbstractLtiIntegrationTest;

/**
 * Nightly Canvas interop, structural counterpart to {@link NightlyLtiMoodleInteropTest}.
 * <p>
 * Container: {@code instructure/canvas-lms} (community image; pinned tag selected when this test is enabled).
 * Canvas requires more bespoke install steps than Moodle (Rails app + Redis + Postgres), so the container
 * harness is intentionally deferred. The test is disabled until that harness lands; the workflow matrix
 * already provisions the slot so enabling it is a single annotation change.
 */
@Tag("nightly-lti")
@Disabled("Phase 3: Canvas Testcontainer harness pending — enable once container + bootstrap helper land")
class NightlyLtiCanvasInteropTest extends AbstractLtiIntegrationTest {

    @Test
    void canvasLaunchRoundTripsThroughArtemis() {
        // Placeholder for the Canvas counterpart of NightlyLtiMoodleInteropTest.moodleLaunchRoundTripsThroughArtemis.
    }
}
