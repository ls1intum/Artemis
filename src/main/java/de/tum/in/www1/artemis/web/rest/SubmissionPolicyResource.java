package de.tum.in.www1.artemis.web.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.submissionpolicy.SubmissionPolicy;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.SubmissionPolicyRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.SubmissionPolicyService;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggle;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

@RestController
@RequestMapping(SubmissionPolicyResource.Endpoints.ROOT)
public class SubmissionPolicyResource {

    private final Logger log = LoggerFactory.getLogger(SubmissionPolicyResource.class);

    public static final String ENTITY_NAME = "programmingExercise.submissionPolicy";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final SubmissionPolicyRepository submissionPolicyRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final SubmissionPolicyService submissionPolicyService;

    public SubmissionPolicyResource(ProgrammingExerciseRepository programmingExerciseRepository, SubmissionPolicyRepository submissionPolicyRepository,
            AuthorizationCheckService authorizationCheckService, SubmissionPolicyService submissionPolicyService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.submissionPolicyRepository = submissionPolicyRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.submissionPolicyService = submissionPolicyService;
    }

    @GetMapping(Endpoints.PROGRAMMING_EXERCISE_SUBMISSION_POLICY)
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<SubmissionPolicy> getSubmissionPolicyOfExercise(@PathVariable Long exerciseId) {
        log.debug("REST request to get submission policy of programming exercise {}", exerciseId);

        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdWithSubmissionPolicyElseThrow(exerciseId);
        authorizationCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, programmingExercise, null);

        return ResponseEntity.ok().body(programmingExercise.getSubmissionPolicy());
    }

    /*
     * Tests: 1. Auth Check (Student, Tutor, Editor) 2. Programming Exercise does not exist 3. Programming Exercise already has submission policy 4. Submission Policy already has
     * an id 5. LRP: Null submission limit 6. LRP: Negative submission limit / 0 submission limit 7. SPP: Null submission limit 8. SPP: Negative submission limit / 0 submission
     * limit 9. SPP: Null penalty 10. SPP: Negative penalty / 0 penalty
     */
    @PostMapping(Endpoints.PROGRAMMING_EXERCISE_SUBMISSION_POLICY)
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<SubmissionPolicy> addSubmissionPolicyToProgrammingExercise(@PathVariable Long exerciseId, @RequestBody SubmissionPolicy submissionPolicy) {
        log.debug("REST request to add submission policy to programming exercise {}", exerciseId);
        HttpHeaders responseHeaders = HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, submissionPolicy.getId() + "");

        SubmissionPolicy addedSubmissionPolicy;
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdWithSubmissionPolicyElseThrow(exerciseId);
        authorizationCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, programmingExercise, null);

        if (programmingExercise.getSubmissionPolicy() != null) {
            responseHeaders = HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "programmingExercisePolicyPresent",
                    "The submission policy could not be added to the programming exercise, because it already has a submission policy.");
            return ResponseEntity.badRequest().headers(responseHeaders).build();
        }

        if (submissionPolicy.getId() != null) {
            responseHeaders = HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "submissionPolicyHasId",
                    "The submission policy could not be added to the programming exercise, because it already has an id.");
            return ResponseEntity.badRequest().headers(responseHeaders).build();
        }
        submissionPolicyService.validateSubmissionPolicy(submissionPolicy);

        addedSubmissionPolicy = submissionPolicyService.addSubmissionPolicyToProgrammingExercise(submissionPolicy, programmingExercise);
        return ResponseEntity.ok().headers(responseHeaders).body(addedSubmissionPolicy);
    }

    @DeleteMapping(Endpoints.PROGRAMMING_EXERCISE_SUBMISSION_POLICY)
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> removeSubmissionPolicyFromProgrammingExercise(@PathVariable Long exerciseId) {
        log.debug("REST request to remove submission policy from programming exercise {}", exerciseId);
        HttpHeaders responseHeaders;

        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdWithSubmissionPolicyElseThrow(exerciseId);
        authorizationCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, programmingExercise, null);

        SubmissionPolicy submissionPolicy = programmingExercise.getSubmissionPolicy();
        if (submissionPolicy == null) {
            responseHeaders = HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "programmingExercisePolicyNotPresent",
                    "The submission policy could not be removed from the programming exercise, because it does not have a submission policy.");
            return ResponseEntity.badRequest().headers(responseHeaders).build();
        }

        submissionPolicyService.removeSubmissionPolicyFromProgrammingExercise(programmingExercise);
        responseHeaders = HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, submissionPolicy.getId() + "");
        return ResponseEntity.ok().headers(responseHeaders).build();
    }

    @PutMapping(Endpoints.ENABLE_SUBMISSION_POLICY)
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<SubmissionPolicy> enableSubmissionPolicy(@PathVariable Long exerciseId) {
        log.debug("REST request to enable the submission policy for programming exercise {}", exerciseId);
        HttpHeaders responseHeaders;

        ProgrammingExercise exercise = programmingExerciseRepository.findByIdWithSubmissionPolicyElseThrow(exerciseId);
        authorizationCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, null);

        SubmissionPolicy submissionPolicy = exercise.getSubmissionPolicy();
        if (submissionPolicy.isActive()) {
            responseHeaders = HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "submissionPolicyAlreadyEnabled",
                    "The submission policy could not be enabled, because it is already active.");
            return ResponseEntity.badRequest().headers(responseHeaders).build();
        }

        submissionPolicyService.enableSubmissionPolicy(submissionPolicy);
        responseHeaders = HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, exerciseId + "");
        return ResponseEntity.ok().headers(responseHeaders).build();
    }

    @PutMapping(Endpoints.DISABLE_SUBMISSION_POLICY)
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<SubmissionPolicy> disableSubmissionPolicy(@PathVariable Long exerciseId) {
        log.debug("REST request to disable the submission policy of programming exercise {}", exerciseId);
        HttpHeaders responseHeaders;

        ProgrammingExercise exercise = programmingExerciseRepository.findByIdWithSubmissionPolicyElseThrow(exerciseId);
        authorizationCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, null);

        SubmissionPolicy submissionPolicy = exercise.getSubmissionPolicy();
        if (!submissionPolicy.isActive()) {
            responseHeaders = HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "submissionPolicyAlreadyDisabled",
                    "The submission policy could not be disabled, because it is already inactive.");
            return ResponseEntity.badRequest().headers(responseHeaders).build();
        }

        submissionPolicyService.disableSubmissionPolicy(submissionPolicy);
        responseHeaders = HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, exerciseId + "");
        return ResponseEntity.ok().headers(responseHeaders).build();
    }

    @PatchMapping(Endpoints.UPDATE_SUBMISSION_POLICY)
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<SubmissionPolicy> updateSubmissionPolicy(@PathVariable Long exerciseId, @RequestBody SubmissionPolicy newSubmissionPolicy) {
        log.debug("REST request to update the submission policy of programming exercise {}", exerciseId);
        HttpHeaders responseHeaders;

        ProgrammingExercise exercise = programmingExerciseRepository.findByIdWithSubmissionPolicyElseThrow(exerciseId);
        authorizationCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, null);

        SubmissionPolicy submissionPolicy = exercise.getSubmissionPolicy();
        if (!submissionPolicy.getClass().getTypeName().equals(newSubmissionPolicy.getClass().getTypeName())) {
            responseHeaders = HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "updatedSubmissionPolicyIncorrectType",
                    "The submission policy could not be updated, because the new type is different from the old type.");
            return ResponseEntity.badRequest().headers(responseHeaders).build();
        }

        submissionPolicyService.validateSubmissionPolicy(newSubmissionPolicy);

        submissionPolicyService.updateSubmissionPolicy(exercise, newSubmissionPolicy);

        responseHeaders = HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, exerciseId + "");
        return ResponseEntity.ok().headers(responseHeaders).build();
    }

    public static final class Endpoints {

        public static final String ROOT = "/api";

        public static final String PROGRAMMING_EXERCISE_SUBMISSION_POLICY = "/programming-exercises/{exerciseId}/submission-policy";

        public static final String ENABLE_SUBMISSION_POLICY = PROGRAMMING_EXERCISE_SUBMISSION_POLICY + "/enable";

        public static final String DISABLE_SUBMISSION_POLICY = PROGRAMMING_EXERCISE_SUBMISSION_POLICY + "/disable";

        public static final String UPDATE_SUBMISSION_POLICY = PROGRAMMING_EXERCISE_SUBMISSION_POLICY + "/update";

    }
}
