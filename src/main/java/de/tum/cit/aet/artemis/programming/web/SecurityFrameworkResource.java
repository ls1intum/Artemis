package de.tum.cit.aet.artemis.programming.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

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

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.dto.SecurityFrameworkConfigDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityFrameworkVersionsDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseMutationGuardService;
import de.tum.cit.aet.artemis.programming.service.security.SecurityFrameworkService;

/**
 * REST controller for the Security Framework activation state of a programming exercise (Objective 1).
 * <p>
 * Reads are open to editors; every mutation requires an instructor. Each mutation commits to the exercise-tests
 * repository and returns the settled state once the commit has been pushed; the transient GENERATING/DELETING
 * states are a client concern.
 */
@Profile(PROFILE_CORE)
@Lazy
@FeatureUsage(UserFeature.PROGRAMMING_SECURITY_FRAMEWORK)
@RestController
@RequestMapping("api/programming/")
public class SecurityFrameworkResource {

    private static final Logger log = LoggerFactory.getLogger(SecurityFrameworkResource.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final SecurityFrameworkService securityFrameworkService;

    private final UserRepository userRepository;

    private final ProgrammingExerciseMutationGuardService mutationGuard;

    public SecurityFrameworkResource(ProgrammingExerciseRepository programmingExerciseRepository, AuthorizationCheckService authorizationCheckService,
            SecurityFrameworkService securityFrameworkService, UserRepository userRepository, ProgrammingExerciseMutationGuardService mutationGuard) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.securityFrameworkService = securityFrameworkService;
        this.userRepository = userRepository;
        this.mutationGuard = mutationGuard;
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
     * GET security-framework/versions : the Ares versions an instructor can pin and the version each template starts with. Not exercise-specific, so the
     * create form can show them before the exercise exists.
     *
     * @return 200 (OK) with the versions in the body
     */
    @GetMapping("security-framework/versions")
    @EnforceAtLeastEditor
    public ResponseEntity<SecurityFrameworkVersionsDTO> getFrameworkVersions() {
        log.debug("REST request to get the Security Framework versions");
        return ResponseEntity.ok(securityFrameworkService.getFrameworkVersions());
    }

    /**
     * PUT programming-exercises/:exerciseId/security-framework/activate : activates Ares: commits the security policy to the tests repository.
     *
     * @param exerciseId       of the programming exercise
     * @param frameworkVersion the Ares version to pin
     * @return 200 (OK) with the resulting config
     */
    @PutMapping("programming-exercises/{exerciseId}/security-framework/activate")
    @EnforceAtLeastInstructor
    public ResponseEntity<SecurityFrameworkConfigDTO> activateSecurityFramework(@PathVariable Long exerciseId, @RequestParam String frameworkVersion) {
        log.debug("REST request to activate the Security Framework of programming exercise {} with framework {}", exerciseId, frameworkVersion);
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        User user = userRepository.getUserWithAuthorities();
        authorizationCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, user);
        // Coordinates with live saves and generation runs that change the same repositories.
        try (var _ = mutationGuard.claimExternalMutation(exerciseId)) {
            return ResponseEntity.ok(securityFrameworkService.activate(exercise, frameworkVersion, user));
        }
    }

    /**
     * PUT programming-exercises/:exerciseId/security-framework/deactivate : deactivates Ares: removes the security policy from the tests repository.
     *
     * @param exerciseId of the programming exercise
     * @return 200 (OK) with the resulting config
     */
    @PutMapping("programming-exercises/{exerciseId}/security-framework/deactivate")
    @EnforceAtLeastInstructor
    public ResponseEntity<SecurityFrameworkConfigDTO> deactivateSecurityFramework(@PathVariable Long exerciseId) {
        log.debug("REST request to deactivate the Security Framework of programming exercise {}", exerciseId);
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        User user = userRepository.getUserWithAuthorities();
        authorizationCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, user);
        // Coordinates with live saves and generation runs that change the same repositories.
        try (var _ = mutationGuard.claimExternalMutation(exerciseId)) {
            return ResponseEntity.ok(securityFrameworkService.deactivate(exercise, user));
        }
    }

    /**
     * PUT programming-exercises/:exerciseId/security-framework/framework-version : pins another Ares version for an active exercise.
     *
     * @param exerciseId       of the programming exercise
     * @param frameworkVersion the Ares version to pin
     * @return 200 (OK) with the resulting config
     */
    @PutMapping("programming-exercises/{exerciseId}/security-framework/framework-version")
    @EnforceAtLeastInstructor
    public ResponseEntity<SecurityFrameworkConfigDTO> updateFrameworkVersion(@PathVariable Long exerciseId, @RequestParam String frameworkVersion) {
        log.debug("REST request to change the Security Framework version of programming exercise {} to {}", exerciseId, frameworkVersion);
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        User user = userRepository.getUserWithAuthorities();
        authorizationCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, user);
        // Coordinates with live saves and generation runs that change the same repositories.
        try (var _ = mutationGuard.claimExternalMutation(exerciseId)) {
            return ResponseEntity.ok(securityFrameworkService.updateFrameworkVersion(exercise, frameworkVersion, user));
        }
    }
}
