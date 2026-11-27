package de.tum.cit.aet.artemis.sandbox.web;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastEditorInExercise;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastInstructorInExercise;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseMutationGuardService;
import de.tum.cit.aet.artemis.sandbox.config.SandboxEnabled;
import de.tum.cit.aet.artemis.sandbox.dto.SandboxConfigurationDTO;
import de.tum.cit.aet.artemis.sandbox.dto.SandboxFrameworkVersionDTO;
import de.tum.cit.aet.artemis.sandbox.dto.SandboxFrameworkVersionsDTO;
import de.tum.cit.aet.artemis.sandbox.service.SandboxActivationService;
import de.tum.cit.aet.artemis.sandbox.service.SandboxConfigurationService;

/**
 * REST controller for the sandbox of a Java programming exercise, i.e. the Ares security policy that restricts what student code may do while the tests run.
 * <p>
 * Reads are open to editors of the exercise's course; every change requires an instructor of it. Each change commits to the exercise-tests repository and
 * returns the configuration once the commit has been pushed.
 */
@Conditional(SandboxEnabled.class)
@Lazy
@FeatureUsage(UserFeature.PROGRAMMING_SECURITY_FRAMEWORK)
@RestController
@RequestMapping("api/sandbox/")
public class SandboxResource {

    private static final Logger log = LoggerFactory.getLogger(SandboxResource.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final SandboxConfigurationService configurationService;

    private final SandboxActivationService activationService;

    private final UserRepository userRepository;

    private final ProgrammingExerciseMutationGuardService mutationGuard;

    public SandboxResource(ProgrammingExerciseRepository programmingExerciseRepository, SandboxConfigurationService configurationService,
            SandboxActivationService activationService, UserRepository userRepository, ProgrammingExerciseMutationGuardService mutationGuard) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.configurationService = configurationService;
        this.activationService = activationService;
        this.userRepository = userRepository;
        this.mutationGuard = mutationGuard;
    }

    /**
     * GET programming-exercises/:exerciseId/configuration : the sandbox configuration of an exercise, read from its exercise-tests repository.
     *
     * @param exerciseId of the programming exercise
     * @return 200 (OK) with the configuration in the body
     */
    @GetMapping("programming-exercises/{exerciseId}/configuration")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<SandboxConfigurationDTO> getConfiguration(@PathVariable long exerciseId) {
        log.debug("REST request to get the sandbox configuration of programming exercise {}", exerciseId);
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        return ResponseEntity.ok(configurationService.getConfiguration(exercise));
    }

    /**
     * GET programming-exercises/:exerciseId/framework-versions : the Ares versions an instructor can pin for an exercise and the version each template
     * starts with. They are the same for every exercise, but only those who can edit an exercise need them.
     *
     * @param exerciseId of the programming exercise
     * @return 200 (OK) with the versions in the body
     */
    @GetMapping("programming-exercises/{exerciseId}/framework-versions")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<SandboxFrameworkVersionsDTO> getFrameworkVersions(@PathVariable long exerciseId) {
        log.debug("REST request to get the sandbox framework versions for programming exercise {}", exerciseId);
        return ResponseEntity.ok(configurationService.getFrameworkVersions());
    }

    /**
     * POST programming-exercises/:exerciseId/configuration/activate : activates the sandbox, i.e. commits the security policy to the tests repository.
     *
     * @param exerciseId of the programming exercise
     * @param request    the Ares version to pin
     * @return 200 (OK) with the resulting configuration
     */
    @PostMapping("programming-exercises/{exerciseId}/configuration/activate")
    @EnforceAtLeastInstructorInExercise
    public ResponseEntity<SandboxConfigurationDTO> activate(@PathVariable long exerciseId, @Valid @RequestBody SandboxFrameworkVersionDTO request) {
        log.debug("REST request to activate the sandbox of programming exercise {} with Ares {}", exerciseId, request.frameworkVersion());
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        User user = userRepository.getUserWithAuthorities();
        // Coordinates with live saves and generation runs that change the same repositories.
        try (var _ = mutationGuard.claimExternalMutation(exerciseId)) {
            return ResponseEntity.ok(activationService.activate(exercise, request.frameworkVersion(), user));
        }
    }

    /**
     * POST programming-exercises/:exerciseId/configuration/deactivate : deactivates the sandbox, i.e. removes the security policy from the tests repository.
     *
     * @param exerciseId of the programming exercise
     * @return 200 (OK) with the resulting configuration
     */
    @PostMapping("programming-exercises/{exerciseId}/configuration/deactivate")
    @EnforceAtLeastInstructorInExercise
    public ResponseEntity<SandboxConfigurationDTO> deactivate(@PathVariable long exerciseId) {
        log.debug("REST request to deactivate the sandbox of programming exercise {}", exerciseId);
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        User user = userRepository.getUserWithAuthorities();
        // Coordinates with live saves and generation runs that change the same repositories.
        try (var _ = mutationGuard.claimExternalMutation(exerciseId)) {
            return ResponseEntity.ok(activationService.deactivate(exercise, user));
        }
    }

    /**
     * PUT programming-exercises/:exerciseId/configuration/framework-version : pins another Ares version for an exercise whose sandbox is active.
     *
     * @param exerciseId of the programming exercise
     * @param request    the Ares version to pin
     * @return 200 (OK) with the resulting configuration
     */
    @PutMapping("programming-exercises/{exerciseId}/configuration/framework-version")
    @EnforceAtLeastInstructorInExercise
    public ResponseEntity<SandboxConfigurationDTO> updateFrameworkVersion(@PathVariable long exerciseId, @Valid @RequestBody SandboxFrameworkVersionDTO request) {
        log.debug("REST request to pin Ares {} for the sandbox of programming exercise {}", request.frameworkVersion(), exerciseId);
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        User user = userRepository.getUserWithAuthorities();
        // Coordinates with live saves and generation runs that change the same repositories.
        try (var _ = mutationGuard.claimExternalMutation(exerciseId)) {
            return ResponseEntity.ok(activationService.updateFrameworkVersion(exercise, request.frameworkVersion(), user));
        }
    }

    /**
     * POST programming-exercises/:exerciseId/configuration/add-missing-test-classes : adds the test classes that the policy files do not list yet.
     *
     * @param exerciseId of the programming exercise
     * @return 200 (OK) with the resulting configuration
     */
    @PostMapping("programming-exercises/{exerciseId}/configuration/add-missing-test-classes")
    @EnforceAtLeastInstructorInExercise
    public ResponseEntity<SandboxConfigurationDTO> addMissingTestClasses(@PathVariable long exerciseId) {
        log.debug("REST request to add the missing test classes to the security policy of programming exercise {}", exerciseId);
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        User user = userRepository.getUserWithAuthorities();
        // Coordinates with live saves and generation runs that change the same repositories.
        try (var _ = mutationGuard.claimExternalMutation(exerciseId)) {
            return ResponseEntity.ok(activationService.addMissingTestClasses(exercise, user));
        }
    }
}
