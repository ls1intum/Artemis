package de.tum.cit.aet.artemis.programming.service.security;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.security.SecurityActivationStatus;
import de.tum.cit.aet.artemis.programming.dto.SecurityFrameworkConfigDTO;

/**
 * Manages the Security Framework activation state of a programming exercise (Objective 1: automatic
 * security policy generation on activation).
 * <p>
 * Persistence is intentionally in-memory only: the state is kept in a map keyed by exercise id for the
 * runtime of the server and no database schema is introduced. This is sufficient for the current UI
 * prototype; a persistent store replaces this map later. The actual policy generation and repository sync
 * are delegated to the {@link Ares2SecurityPolicyService} boundary, and version compatibility to
 * {@link Ares2FrameworkCompatibilityService} (both mocked until the real Ares2 integration exists). The
 * client owns the transient GENERATING/DELETING states while these calls are in flight.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class SecurityFrameworkService {

    private static final Logger log = LoggerFactory.getLogger(SecurityFrameworkService.class);

    public static final String ENTITY_NAME = "programmingExercise.securityFramework";

    /** In-memory activation state per exercise id. No database table is used. */
    private final Map<Long, SecurityFrameworkConfigDTO> configByExerciseId = new ConcurrentHashMap<>();

    private final Ares2SecurityPolicyService ares2SecurityPolicyService;

    private final Ares2FrameworkCompatibilityService ares2FrameworkCompatibilityService;

    public SecurityFrameworkService(Ares2SecurityPolicyService ares2SecurityPolicyService, Ares2FrameworkCompatibilityService ares2FrameworkCompatibilityService) {
        this.ares2SecurityPolicyService = ares2SecurityPolicyService;
        this.ares2FrameworkCompatibilityService = ares2FrameworkCompatibilityService;
    }

    /**
     * @return the framework versions the (mocked) Ares2 integration supports, newest first.
     */
    public List<String> getSupportedFrameworkVersions() {
        return ares2FrameworkCompatibilityService.getSupportedFrameworkVersions();
    }

    /**
     * @param exercise the programming exercise
     * @return the stored Security Framework config, or a default inactive config if none has been stored yet.
     */
    public SecurityFrameworkConfigDTO getConfig(ProgrammingExercise exercise) {
        return configByExerciseId.getOrDefault(exercise.getId(), defaultInactiveConfig());
    }

    /**
     * Activates the sandbox: generates the implicit default policy for the given framework version, commits
     * it to the exercise-tests repository (via Ares2) and stores the resulting ACTIVE state in memory.
     */
    public SecurityFrameworkConfigDTO activate(ProgrammingExercise exercise, String frameworkVersion) {
        validateFrameworkVersion(frameworkVersion);
        String commitHash = ares2SecurityPolicyService.createAndCommitPolicy(exercise, frameworkVersion);
        SecurityFrameworkConfigDTO config = new SecurityFrameworkConfigDTO(SecurityActivationStatus.ACTIVE.name(), frameworkVersion, commitHash, Instant.now().toString());
        configByExerciseId.put(exercise.getId(), config);
        log.debug("Activated the Security Framework for exercise {} (framework {}, commit {})", exercise.getId(), frameworkVersion, commitHash);
        return config;
    }

    /**
     * Deactivates the sandbox: removes the policy from the exercise-tests repository (via Ares2) and stores
     * the resulting INACTIVE state in memory.
     */
    public SecurityFrameworkConfigDTO deactivate(ProgrammingExercise exercise) {
        ares2SecurityPolicyService.removePolicy(exercise);
        SecurityFrameworkConfigDTO config = new SecurityFrameworkConfigDTO(SecurityActivationStatus.INACTIVE.name(), defaultFrameworkVersion(), null, null);
        configByExerciseId.put(exercise.getId(), config);
        log.debug("Deactivated the Security Framework for exercise {}", exercise.getId());
        return config;
    }

    /**
     * Re-syncs an already active exercise onto a different framework version (re-generates and re-commits the policy).
     */
    public SecurityFrameworkConfigDTO updateFrameworkVersion(ProgrammingExercise exercise, String frameworkVersion) {
        validateFrameworkVersion(frameworkVersion);
        SecurityFrameworkConfigDTO current = configByExerciseId.get(exercise.getId());
        if (current == null || !SecurityActivationStatus.ACTIVE.name().equals(current.status())) {
            throw new BadRequestAlertException("The framework version cannot be changed because the Security Framework is not active for this exercise.", ENTITY_NAME,
                    "securityFrameworkNotActive");
        }
        String commitHash = ares2SecurityPolicyService.createAndCommitPolicy(exercise, frameworkVersion);
        SecurityFrameworkConfigDTO config = new SecurityFrameworkConfigDTO(SecurityActivationStatus.ACTIVE.name(), frameworkVersion, commitHash, Instant.now().toString());
        configByExerciseId.put(exercise.getId(), config);
        log.debug("Re-synced the Security Framework of exercise {} to framework {} (commit {})", exercise.getId(), frameworkVersion, commitHash);
        return config;
    }

    private void validateFrameworkVersion(String frameworkVersion) {
        if (!ares2FrameworkCompatibilityService.isFrameworkVersionSupported(frameworkVersion)) {
            throw new BadRequestAlertException("Unsupported Security Framework version: " + frameworkVersion, ENTITY_NAME, "unsupportedFrameworkVersion");
        }
    }

    private SecurityFrameworkConfigDTO defaultInactiveConfig() {
        return new SecurityFrameworkConfigDTO(SecurityActivationStatus.INACTIVE.name(), defaultFrameworkVersion(), null, null);
    }

    private String defaultFrameworkVersion() {
        List<String> supportedVersions = ares2FrameworkCompatibilityService.getSupportedFrameworkVersions();
        return supportedVersions.isEmpty() ? "" : supportedVersions.getFirst();
    }
}
