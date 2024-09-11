package de.tum.cit.aet.artemis.web.rest;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.domain.submissionpolicy.SubmissionPolicy;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.service.ParticipationAuthorizationCheckService;
import de.tum.cit.aet.artemis.service.SubmissionPolicyService;
import de.tum.cit.aet.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.cit.aet.artemis.web.rest.util.HeaderUtil;

@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class SubmissionPolicyResource {

    private static final Logger log = LoggerFactory.getLogger(SubmissionPolicyResource.class);

    public static final String ENTITY_NAME = "programmingExercise.submissionPolicy";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final SubmissionPolicyService submissionPolicyService;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final ParticipationAuthorizationCheckService participationAuthCheckService;

    public SubmissionPolicyResource(ProgrammingExerciseRepository programmingExerciseRepository, AuthorizationCheckService authorizationCheckService,
            SubmissionPolicyService submissionPolicyService, ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository,
            ParticipationAuthorizationCheckService participationAuthCheckService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.submissionPolicyService = submissionPolicyService;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.participationAuthCheckService = participationAuthCheckService;
    }

    /**
     * GET programming-exercises/:exerciseId/submission-policy : Gets the submission policy of a programming exercise
     *
     * @param exerciseId of the programming exercise for which the submission policy should be fetched
     * @return the ResponseEntity with status 200 (OK) and the submission policy in body. Status 404 when
     *         the programming exercise does not exist and status 403 when the requester is not at least a student
     *         in the course the programming exercise belongs to.
     */
    @GetMapping("programming-exercises/{exerciseId}/submission-policy")
    @EnforceAtLeastStudent
    public ResponseEntity<SubmissionPolicy> getSubmissionPolicyOfExercise(@PathVariable Long exerciseId) {
        log.debug("REST request to get submission policy of programming exercise {}", exerciseId);

        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdWithSubmissionPolicyElseThrow(exerciseId);
        authorizationCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, programmingExercise, null);

        return ResponseEntity.ok().body(programmingExercise.getSubmissionPolicy());
    }

    /**
     * POST programming-exercises/:exerciseId/submission-policy
     * <br>
     * <br>
     * Adds a submission policy to the programming exercise. When a submission policy is added to a programming
     * exercise retroactively, it is disabled by default. More information on adding submission policies
     * can be found at {@link SubmissionPolicyService#addSubmissionPolicyToProgrammingExercise(SubmissionPolicy, ProgrammingExercise)}.
     *
     * @param exerciseId       of the programming exercise for which the submission policy in request body should be added
     * @param submissionPolicy that should be added to the programming exercise
     * @return the ResponseEntity with status 200 (OK) and the added submission policy in body. Status 404 when
     *         the programming exercise does not exist, status 403 when the requester is not at least an instructor
     *         in the course the programming exercise belongs to and 400 when the submission policy has an id or
     *         is invalid. More information on submission policy validation can be found at {@link SubmissionPolicyService#validateSubmissionPolicy(SubmissionPolicy)}.
     */
    @PostMapping("programming-exercises/{exerciseId}/submission-policy")
    @EnforceAtLeastInstructor
    public ResponseEntity<SubmissionPolicy> addSubmissionPolicyToProgrammingExercise(@PathVariable Long exerciseId, @RequestBody SubmissionPolicy submissionPolicy)
            throws URISyntaxException {
        log.debug("REST request to add submission policy to programming exercise {}", exerciseId);

        SubmissionPolicy addedSubmissionPolicy;
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdWithSubmissionPolicyElseThrow(exerciseId);
        authorizationCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, programmingExercise, null);

        if (programmingExercise.getSubmissionPolicy() != null) {
            throw new BadRequestAlertException("The submission policy could not be added to the programming exercise, because it already has a submission policy.", ENTITY_NAME,
                    "programmingExercisePolicyPresent");
        }

        if (submissionPolicy.getId() != null) {
            throw new BadRequestAlertException("The submission policy could not be added to the programming exercise, because it already has an id.", ENTITY_NAME,
                    "submissionPolicyHasId");
        }
        submissionPolicyService.validateSubmissionPolicy(submissionPolicy);

        addedSubmissionPolicy = submissionPolicyService.addSubmissionPolicyToProgrammingExercise(submissionPolicy, programmingExercise);
        HttpHeaders responseHeaders = HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, Long.toString(addedSubmissionPolicy.getId()));

        return ResponseEntity.created(new URI("programming-exercises/" + exerciseId + "/submission-policy")).headers(responseHeaders).body(addedSubmissionPolicy);
    }

    /**
     * DELETE programming-exercises/:exerciseId/submission-policy
     * <br>
     * <br>
     * Removes the submission policy of a programming exercise. When a submission policy is removed from a programming
     * exercise, the submission policy effect is removed from every participation. More information on removing submission policies
     * can be found at {@link SubmissionPolicyService#removeSubmissionPolicyFromProgrammingExercise(ProgrammingExercise)}.
     *
     * @param exerciseId of the programming exercise for which the submission policy should be deleted
     * @return the ResponseEntity with status 200 (OK) when the submission policy was removed successfully. Status 404 when
     *         the programming exercise does not exist, status 403 when the requester is not at least an instructor
     *         in the course the programming exercise belongs to and 400 when the programming exercise does not have a submission policy.
     */
    @DeleteMapping("programming-exercises/{exerciseId}/submission-policy")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> removeSubmissionPolicyFromProgrammingExercise(@PathVariable Long exerciseId) {
        log.debug("REST request to remove submission policy from programming exercise {}", exerciseId);

        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdWithSubmissionPolicyElseThrow(exerciseId);
        authorizationCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, programmingExercise, null);

        SubmissionPolicy submissionPolicy = programmingExercise.getSubmissionPolicy();
        if (submissionPolicy == null) {
            throw new BadRequestAlertException("The submission policy could not be removed from the programming exercise, because it does not have a submission policy.",
                    ENTITY_NAME, "programmingExercisePolicyNotPresent");
        }

        submissionPolicyService.removeSubmissionPolicyFromProgrammingExercise(programmingExercise);
        HttpHeaders responseHeaders = HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, Long.toString(submissionPolicy.getId()));
        return ResponseEntity.ok().headers(responseHeaders).build();
    }

    /**
     * PUT programming-exercises/:exerciseId/submission-policy
     * <br>
     * <br>
     * Either activates or deactivates the submission policy of a programming exercise, depending on the activate
     * request parameter. Toggling the activation of a submission policy has an immediate effect on student participations.
     * More information can be found at {@link SubmissionPolicyService#enableSubmissionPolicy(SubmissionPolicy)} and
     * {@link SubmissionPolicyService#disableSubmissionPolicy(SubmissionPolicy)}.
     *
     * @param exerciseId of the programming exercise for which the submission policy should be toggled
     * @param activate   specifies whether the submission policy should be enabled or disabled
     * @return the ResponseEntity with status 200 (OK) when the submission policy was enabled or disabled. Status 404 when
     *         the programming exercise does not exist, status 403 when the requester is not at least an instructor
     *         in the course the programming exercise belongs to and 400 when activate matches the current status of
     *         the submission policy or the programming exercise has no submission policy.
     */
    @PutMapping("programming-exercises/{exerciseId}/submission-policy")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> toggleSubmissionPolicy(@PathVariable Long exerciseId, @RequestParam Boolean activate) {
        log.debug("REST request to toggle the submission policy for programming exercise {}", exerciseId);
        HttpHeaders responseHeaders;

        ProgrammingExercise exercise = programmingExerciseRepository.findByIdWithSubmissionPolicyElseThrow(exerciseId);
        authorizationCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, null);

        SubmissionPolicy submissionPolicy = exercise.getSubmissionPolicy();
        if (submissionPolicy == null) {
            throw new BadRequestAlertException("The submission policy could not be toggled, because the programming exercise does not have a submission policy.", ENTITY_NAME,
                    "submissionPolicyToggleFailedPolicyNotExist");
        }
        submissionPolicy.setProgrammingExercise(exercise);
        if (activate == submissionPolicy.isActive()) {
            String errorKey = activate ? "submissionPolicyAlreadyEnabled" : "submissionPolicyAlreadyDisabled";
            String defaultMessage = activate ? "The submission policy could not be enabled, because it is already active."
                    : "The submission policy could not be disabled, because it is not active.";

            throw new BadRequestAlertException(defaultMessage, ENTITY_NAME, errorKey);
        }
        if (activate) {
            submissionPolicyService.enableSubmissionPolicy(submissionPolicy);
        }
        else {
            submissionPolicyService.disableSubmissionPolicy(submissionPolicy);
        }
        responseHeaders = HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, Long.toString(submissionPolicy.getId()));
        return ResponseEntity.ok().headers(responseHeaders).build();
    }

    /**
     * PATCH programming-exercises/:exerciseId/submission-policy
     * <br>
     * <br>
     * Updates the submission policy of a programming exercise. When a submission policy is updated, the system applies
     * the effect of the submission policy immediately. More information on updating submission policies can be found at
     * {@link SubmissionPolicyService#updateSubmissionPolicy(ProgrammingExercise, SubmissionPolicy)}.
     *
     * @param exerciseId              of the programming exercise for which the submission policy in request body should be added
     * @param updatedSubmissionPolicy that should replace the old submission policy
     * @return the ResponseEntity with status 200 (OK) and the updated submission policy in body. Status 404 when
     *         the programming exercise does not exist, status 403 when the requester is not at least an instructor
     *         in the course the programming exercise belongs to and 400 when the submission policy is invalid.
     *         More information on submission policy validation can be found at
     *         {@link SubmissionPolicyService#validateSubmissionPolicy(SubmissionPolicy)}.
     */
    @PatchMapping("programming-exercises/{exerciseId}/submission-policy")
    @EnforceAtLeastInstructor
    public ResponseEntity<SubmissionPolicy> updateSubmissionPolicy(@PathVariable Long exerciseId, @RequestBody SubmissionPolicy updatedSubmissionPolicy) {
        log.debug("REST request to update the submission policy of programming exercise {}", exerciseId);
        HttpHeaders responseHeaders;

        ProgrammingExercise exercise = programmingExerciseRepository.findByIdWithSubmissionPolicyElseThrow(exerciseId);
        authorizationCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, null);

        SubmissionPolicy submissionPolicy = exercise.getSubmissionPolicy();
        if (submissionPolicy == null) {
            throw new BadRequestAlertException("The submission policy could not be updated, because the programming exercise does not have a submission policy.", ENTITY_NAME,
                    "submissionPolicyUpdateFailedPolicyNotExist");
        }

        submissionPolicyService.validateSubmissionPolicy(updatedSubmissionPolicy);
        submissionPolicy.setProgrammingExercise(exercise);
        submissionPolicy = submissionPolicyService.updateSubmissionPolicy(exercise, updatedSubmissionPolicy);

        responseHeaders = HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, Long.toString(submissionPolicy.getId()));
        return ResponseEntity.ok().headers(responseHeaders).body(submissionPolicy);
    }

    /**
     * GET participations/:participationId/submission-count
     * <br>
     * <br>
     * Retrieves the amount of submissions that are counted for the submission policy. These are all submissions that have at least one result.
     *
     * @param participationId of the participation for which the submission count should be retrieved
     * @return the ResponseEntity with status 200 (OK) containing the number of submissions in its body.
     */
    @GetMapping("participations/{participationId}/submission-count")
    @EnforceAtLeastStudent
    public ResponseEntity<Integer> getParticipationSubmissionCount(@PathVariable Long participationId) {
        var programmingExerciseStudentParticipation = programmingExerciseStudentParticipationRepository.findByIdElseThrow(participationId);
        participationAuthCheckService.checkCanAccessParticipationElseThrow(programmingExerciseStudentParticipation);

        return ResponseEntity.ok().body(submissionPolicyService.getParticipationSubmissionCount(programmingExerciseStudentParticipation, false));
    }
}
