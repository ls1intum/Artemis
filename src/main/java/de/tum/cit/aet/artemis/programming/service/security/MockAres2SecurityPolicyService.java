package de.tum.cit.aet.artemis.programming.service.security;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SECURITY_FRAMEWORK;

import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Mock stand-in for Ares2's policy generation and repository sync. Instead of generating a real policy
 * and committing it to the exercise-tests repository, it logs the request and returns a synthetic commit
 * hash. Replace with a real Ares2-backed implementation when that integration lands.
 * <p>
 * Because it installs nothing, it - and the whole Security Framework feature - is registered only under the
 * {@code securityframework} profile ({@link de.tum.cit.aet.artemis.core.config.Constants#PROFILE_SECURITY_FRAMEWORK}),
 * never in a normal deployment. A deployment without that profile has no policy implementation and does not expose
 * the feature at all, so activation can never report a committed and enforcing policy where none was installed.
 */
@Profile(PROFILE_SECURITY_FRAMEWORK)
@Lazy
@Service
public class MockAres2SecurityPolicyService implements Ares2SecurityPolicyService {

    private static final Logger log = LoggerFactory.getLogger(MockAres2SecurityPolicyService.class);

    /**
     * Simulated durations of the Ares2 work, in ms. A real integration generates the implicit default
     * policy and commits it to the exercise-tests repository (and removes it on deactivation), which takes
     * noticeable time. Simulating it keeps the client's transient GENERATING/DELETING states visible
     * instead of flashing by, and models the real round-trip. Removal is a bit quicker than generation.
     */
    private static final long POLICY_GENERATION_DURATION_MS = 1800;

    private static final long POLICY_REMOVAL_DURATION_MS = 1400;

    @Override
    public String createAndCommitPolicy(ProgrammingExercise exercise, String frameworkVersion) {
        log.debug("[MOCK Ares2] Generating and committing the implicit default policy for exercise {} with framework version {}", exercise.getId(), frameworkVersion);
        simulateWork(POLICY_GENERATION_DURATION_MS);
        return syntheticCommitHash();
    }

    @Override
    public String removePolicy(ProgrammingExercise exercise) {
        log.debug("[MOCK Ares2] Removing the security policy from the exercise-tests repository of exercise {}", exercise.getId());
        simulateWork(POLICY_REMOVAL_DURATION_MS);
        return syntheticCommitHash();
    }

    /**
     * Blocks the request thread to simulate the time Ares2 spends generating/committing or removing the
     * policy. Mock-only; the real Ares2 integration will take the time it takes and this will be removed.
     */
    private void simulateWork(long durationMs) {
        try {
            Thread.sleep(durationMs);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String syntheticCommitHash() {
        byte[] bytes = new byte[4];
        ThreadLocalRandom.current().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes).substring(0, 7);
    }
}
