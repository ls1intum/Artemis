package de.tum.cit.aet.artemis.programming.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SECURITY_FRAMEWORK;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.dto.SecurityFrameworkConfigDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.security.SecurityFrameworkService;

/**
 * REST controller for the Security Framework activation state of a programming exercise (Objective 1).
 * <p>
 * Reads are open to editors; every mutation requires an instructor. The transient GENERATING/DELETING
 * states are a client concern - each endpoint here returns the settled config once the (mocked) Ares2
 * sync completes.
 */
@Profile(PROFILE_SECURITY_FRAMEWORK)
@Lazy
@FeatureUsage(UserFeature.PROGRAMMING_SECURITY_FRAMEWORK)
@RestController
@RequestMapping("api/programming/")
public class SecurityFrameworkResource {

    private static final Logger log = LoggerFactory.getLogger(SecurityFrameworkResource.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final SecurityFrameworkService securityFrameworkService;

    public SecurityFrameworkResource(ProgrammingExerciseRepository programmingExerciseRepository, AuthorizationCheckService authorizationCheckService,
            SecurityFrameworkService securityFrameworkService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.securityFrameworkService = securityFrameworkService;
    }

    /**
     * GET programming-exercises/:exerciseId/security-framework : the Security Framework config of an exercise.
     *
     * @param exerciseId of the programming exercise
     * @return 200 (OK) with the config in the body
     */
    @GetMapping("programming-exercises/{exerciseId}/security-framework")
    @EnforceAtLeastEditor
    public ResponseEntity<SecurityFrameworkConfigDTO> getSecurityFrameworkConfig(@PathVariable Long exerciseId) {
        log.debug("REST request to get the Security Framework config of programming exercise {}", exerciseId);
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authorizationCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, null);
        return ResponseEntity.ok(securityFrameworkService.getConfig(exercise));
    }

    /**
     * GET programming-exercises/:exerciseId/security-framework/supported-versions : the selectable framework versions.
     *
     * @param exerciseId of the programming exercise
     * @return 200 (OK) with the supported framework versions in the body
     */
    @GetMapping("programming-exercises/{exerciseId}/security-framework/supported-versions")
    @EnforceAtLeastEditor
    public ResponseEntity<List<String>> getSupportedFrameworkVersions(@PathVariable Long exerciseId) {
        log.debug("REST request to get the supported Security Framework versions for programming exercise {}", exerciseId);
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authorizationCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, null);
        return ResponseEntity.ok(securityFrameworkService.getSupportedFrameworkVersions());
    }

    /**
     * PUT programming-exercises/:exerciseId/security-framework/activate : activates the sandbox and generates the policy.
     *
     * @param exerciseId       of the programming exercise
     * @param frameworkVersion the framework version to generate the policy with
     * @return 200 (OK) with the resulting ACTIVE config
     */
    @PutMapping("programming-exercises/{exerciseId}/security-framework/activate")
    @EnforceAtLeastInstructor
    public ResponseEntity<SecurityFrameworkConfigDTO> activateSecurityFramework(@PathVariable Long exerciseId, @RequestParam String frameworkVersion) {
        log.debug("REST request to activate the Security Framework of programming exercise {} with framework {}", exerciseId, frameworkVersion);
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authorizationCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, null);
        return ResponseEntity.ok(securityFrameworkService.activate(exercise, frameworkVersion));
    }

    /**
     * PUT programming-exercises/:exerciseId/security-framework/deactivate : deactivates the sandbox and removes the policy.
     *
     * @param exerciseId of the programming exercise
     * @return 200 (OK) with the resulting INACTIVE config
     */
    @PutMapping("programming-exercises/{exerciseId}/security-framework/deactivate")
    @EnforceAtLeastInstructor
    public ResponseEntity<SecurityFrameworkConfigDTO> deactivateSecurityFramework(@PathVariable Long exerciseId) {
        log.debug("REST request to deactivate the Security Framework of programming exercise {}", exerciseId);
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authorizationCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, null);
        return ResponseEntity.ok(securityFrameworkService.deactivate(exercise));
    }

    /**
     * PUT programming-exercises/:exerciseId/security-framework/framework-version : re-syncs an active exercise onto another version.
     *
     * @param exerciseId       of the programming exercise
     * @param frameworkVersion the framework version to switch to
     * @return 200 (OK) with the resulting ACTIVE config
     */
    @PutMapping("programming-exercises/{exerciseId}/security-framework/framework-version")
    @EnforceAtLeastInstructor
    public ResponseEntity<SecurityFrameworkConfigDTO> updateFrameworkVersion(@PathVariable Long exerciseId, @RequestParam String frameworkVersion) {
        log.debug("REST request to change the Security Framework version of programming exercise {} to {}", exerciseId, frameworkVersion);
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authorizationCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, null);
        return ResponseEntity.ok(securityFrameworkService.updateFrameworkVersion(exercise, frameworkVersion));
    }
}
