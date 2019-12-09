package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.service.*;

/** REST controller for managing ProgrammingAssessment. */
@RestController
@RequestMapping("/api")
public class ProgrammingAssessmentResource extends AssessmentResource {

    private final Logger log = LoggerFactory.getLogger(ProgrammingAssessmentResource.class);

    private static final String ENTITY_NAME = "programmingAssessment";

    private final ProgrammingAssessmentService programmingAssessmentService;

    private final ProgrammingSubmissionService programmingSubmissionService;

    public ProgrammingAssessmentResource(AuthorizationCheckService authCheckService, UserService userService, ProgrammingAssessmentService programmingAssessmentService,
            ProgrammingSubmissionService programmingSubmissionService) {
        super(authCheckService, userService);
        this.programmingAssessmentService = programmingAssessmentService;
        this.programmingSubmissionService = programmingSubmissionService;
    }

    /**
     * Update an assessment after a complaint was accepted.
     *
     * @param submissionId the id of the submission for which the assessment should be updated
     * @param assessmentUpdate the programing assessment update
     * @return the updated result
     */
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/programming-submissions/{submissionId}/assessment-after-complaint")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> updateProgrammingManualResultAfterComplaint(@RequestBody ProgrammingAssessmentUpdate assessmentUpdate, @PathVariable long submissionId) {
        log.debug("REST request to update the assessment of manual result for submission {} after complaint.", submissionId);
        User user = userService.getUserWithGroupsAndAuthorities();
        ProgrammingSubmission programmingSubmission = programmingSubmissionService.findByIdWithEagerResultAndFeedback(submissionId);
        ProgrammingExercise programmingExercise = (ProgrammingExercise) programmingSubmission.getParticipation().getExercise();
        checkAuthorization(programmingExercise, user);
        if (!programmingExercise.areManualResultsAllowed()) {
            return forbidden();
        }

        Result result = programmingAssessmentService.updateAssessmentAfterComplaint(programmingSubmission.getResult(), programmingExercise, assessmentUpdate);
        // make sure the submission is reconnected with the result to prevent problems when the object is used for other calls in the client
        result.setSubmission(programmingSubmission);
        // remove circular dependencies if the results of the participation are there
        if (result.getParticipation() != null && Hibernate.isInitialized(result.getParticipation().getResults()) && result.getParticipation().getResults() != null) {
            result.getParticipation().setResults(null);
        }

        if (result.getParticipation() != null && result.getParticipation() instanceof StudentParticipation
                && !authCheckService.isAtLeastInstructorForExercise(programmingExercise, user)) {
            ((StudentParticipation) result.getParticipation()).filterSensitiveInformation();
        }

        return ResponseEntity.ok(result);
    }

    @Override
    String getEntityName() {
        return ENTITY_NAME;
    }
}
