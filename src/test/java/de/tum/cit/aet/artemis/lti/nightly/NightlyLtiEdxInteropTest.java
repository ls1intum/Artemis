package de.tum.cit.aet.artemis.lti.nightly;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.lti.AbstractLtiIntegrationTest;

/**
 * Nightly Open edX interop, structural counterpart to {@link NightlyLtiMoodleInteropTest}.
 * <p>
 * Container: {@code edxops/devstack} (or its successor tutor-based image). edX's devstack image is heavy and the
 * LTI 1.3 tool registration path differs from Moodle's — deferred until the Phase 2 Moodle suite proves stable.
 */
@Tag("nightly-lti")
@Disabled("Phase 3: edX Testcontainer harness pending — enable once container + bootstrap helper land")
class NightlyLtiEdxInteropTest extends AbstractLtiIntegrationTest {

    @Test
    void edxLaunchRoundTripsThroughArtemis() {
        // Placeholder for the edX counterpart of NightlyLtiMoodleInteropTest.moodleLaunchRoundTripsThroughArtemis.
    }
}
