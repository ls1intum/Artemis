package de.tum.cit.aet.artemis.programming.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastEditorInExercise;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastInstructorInExercise;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.dto.SecurityPolicyChangeDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityPolicyCommitResultDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityPolicyEditorDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityPolicyImportDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityPolicyPreviewDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityTemplateFilesDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityTestStructureDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseMutationGuardService;
import de.tum.cit.aet.artemis.programming.service.security.SecurityPolicyEditorService;

/**
 * REST controller for the policy editor of the Security Framework (Objective 2): reading, previewing and committing the security policies of a programming
 * exercise, and the test structure and template files the editor offers for selection.
 * <p>
 * Like the activation endpoints, reads are open to editors of the exercise's course and committing requires an instructor of it. A preview changes nothing,
 * so editors may request it.
 */
@Profile(PROFILE_CORE)
@Lazy
@FeatureUsage(UserFeature.PROGRAMMING_SECURITY_FRAMEWORK)
@RestController
@RequestMapping("api/programming/")
public class SecurityPolicyResource {

    private static final Logger log = LoggerFactory.getLogger(SecurityPolicyResource.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final SecurityPolicyEditorService securityPolicyEditorService;

    private final UserRepository userRepository;

    private final ProgrammingExerciseMutationGuardService mutationGuard;

    public SecurityPolicyResource(ProgrammingExerciseRepository programmingExerciseRepository, SecurityPolicyEditorService securityPolicyEditorService,
            UserRepository userRepository, ProgrammingExerciseMutationGuardService mutationGuard) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.securityPolicyEditorService = securityPolicyEditorService;
        this.userRepository = userRepository;
        this.mutationGuard = mutationGuard;
    }

    /**
     * GET programming-exercises/:exerciseId/security-framework/policies-with-config : the security policies and the Security Framework state of an
     * exercise, read from the same commit of the tests repository.
     *
     * @param exerciseId of the programming exercise
     * @return 200 (OK) with the policies and the state in the body
     */
    @GetMapping("programming-exercises/{exerciseId}/security-framework/policies-with-config")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<SecurityPolicyCommitResultDTO> getSecurityPoliciesWithConfig(@PathVariable Long exerciseId) {
        log.debug("REST request to get the security policies and the Security Framework state of programming exercise {}", exerciseId);
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        return ResponseEntity.ok(securityPolicyEditorService.getEditorWithConfig(exercise));
    }

    /**
     * GET programming-exercises/:exerciseId/security-framework/policies : the security policies of an exercise, as the editor shows them.
     *
     * @param exerciseId of the programming exercise
     * @return 200 (OK) with the policies in the body
     */
    @GetMapping("programming-exercises/{exerciseId}/security-framework/policies")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<SecurityPolicyEditorDTO> getSecurityPolicies(@PathVariable Long exerciseId) {
        log.debug("REST request to get the security policies of programming exercise {}", exerciseId);
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        return ResponseEntity.ok(securityPolicyEditorService.getEditor(exercise));
    }

    /**
     * POST programming-exercises/:exerciseId/security-framework/policies/preview : renders changed policies without committing them and checks them against
     * the pinned Ares version.
     *
     * @param exerciseId of the programming exercise
     * @param change     the changed policies
     * @return 200 (OK) with the files before and after the change
     */
    @PostMapping("programming-exercises/{exerciseId}/security-framework/policies/preview")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<SecurityPolicyPreviewDTO> previewSecurityPolicies(@PathVariable Long exerciseId, @RequestBody SecurityPolicyChangeDTO change) {
        log.debug("REST request to preview changed security policies of programming exercise {}", exerciseId);
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        return ResponseEntity.ok(securityPolicyEditorService.preview(exercise, change));
    }

    /**
     * POST programming-exercises/:exerciseId/security-framework/policies/import : reads an imported policy file like a committed one, without writing it.
     *
     * @param exerciseId   of the programming exercise
     * @param importedFile the path of the policy it replaces and the content of the file
     * @return 200 (OK) with the policy, or why the editor cannot edit it
     */
    @PostMapping("programming-exercises/{exerciseId}/security-framework/policies/import")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<SecurityPolicyEditorDTO.PolicyDTO> importSecurityPolicy(@PathVariable Long exerciseId, @RequestBody SecurityPolicyImportDTO importedFile) {
        log.debug("REST request to import a security policy into the editor of programming exercise {}", exerciseId);
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        return ResponseEntity.ok(securityPolicyEditorService.importPolicy(exercise, importedFile));
    }

    /**
     * PUT programming-exercises/:exerciseId/security-framework/policies : commits changed policies to the tests repository and pushes them.
     *
     * @param exerciseId of the programming exercise
     * @param change     the changed policies, with the commit the editor loaded them from
     * @return 200 (OK) with the policies and the Security Framework state after the commit, or 409 (Conflict) if the policies changed since they were loaded
     */
    @PutMapping("programming-exercises/{exerciseId}/security-framework/policies")
    @EnforceAtLeastInstructorInExercise
    public ResponseEntity<SecurityPolicyCommitResultDTO> commitSecurityPolicies(@PathVariable Long exerciseId, @RequestBody SecurityPolicyChangeDTO change) {
        log.debug("REST request to commit changed security policies of programming exercise {}", exerciseId);
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        User user = userRepository.getUserWithAuthorities();
        // Coordinates with live saves and generation runs that change the same repositories.
        try (var _ = mutationGuard.claimExternalMutation(exerciseId)) {
            return ResponseEntity.ok(securityPolicyEditorService.commit(exercise, change, user));
        }
    }

    /**
     * GET programming-exercises/:exerciseId/security-framework/test-structure : the test classes of the tests repository with their test cases.
     *
     * @param exerciseId of the programming exercise
     * @param commit     the commit to read, e.g. the one the editor loaded the policies from; absent for the head commit
     * @return 200 (OK) with the test structure in the body, or 409 (Conflict) if the tests repository has no such commit
     */
    @GetMapping("programming-exercises/{exerciseId}/security-framework/test-structure")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<SecurityTestStructureDTO> getTestStructure(@PathVariable Long exerciseId, @RequestParam(required = false) @Nullable String commit) {
        log.debug("REST request to get the test structure of programming exercise {}", exerciseId);
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        return ResponseEntity.ok(securityPolicyEditorService.getTestStructure(exercise, commit));
    }

    /**
     * GET programming-exercises/:exerciseId/security-framework/template-files : the files of the template repository.
     *
     * @param exerciseId of the programming exercise
     * @return 200 (OK) with the paths of the files in the body
     */
    @GetMapping("programming-exercises/{exerciseId}/security-framework/template-files")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<SecurityTemplateFilesDTO> getTemplateFiles(@PathVariable Long exerciseId) {
        log.debug("REST request to get the template files of programming exercise {}", exerciseId);
        // The template repository is known through the template participation, which is not loaded with the exercise.
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdWithTemplateParticipationElseThrow(exerciseId);
        return ResponseEntity.ok(securityPolicyEditorService.getTemplateFiles(exercise));
    }
}
