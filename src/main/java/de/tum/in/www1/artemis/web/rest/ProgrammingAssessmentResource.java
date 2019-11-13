package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import java.time.ZonedDateTime;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.service.*;

/** REST controller for managing ProgrammingAssessment. */
@RestController
@RequestMapping("/api")
public class ProgrammingAssessmentResource extends AssessmentResource {

    private final Logger log = LoggerFactory.getLogger(ProgrammingAssessmentResource.class);

    private static final String ENTITY_NAME = "programmingAssessment";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ProgrammingExerciseService programmingExerciseService;

    private final ProgrammingAssessmentService programmingAssessmentService;

    private final ProgrammingSubmissionService programmingSubmissionService;

    public ProgrammingAssessmentResource(AuthorizationCheckService authCheckService, UserService userService, ProgrammingExerciseService programmingExerciseService,
            ProgrammingAssessmentService programmingAssessmentService, ProgrammingSubmissionService programmingSubmissionService) {
        super(authCheckService, userService);
        this.programmingExerciseService = programmingExerciseService;
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
    public ResponseEntity<Result> updateProgrammingManualResultAfterComplaint(@RequestBody ProgrammingAssessmentUpdate assessmentUpdate, @PathVariable Long submissionId) {
        log.debug("REST request to update the assessment of manual result for submission {} after complaint.", submissionId);
        User user = userService.getUserWithGroupsAndAuthorities();
        ProgrammingSubmission programmingSubmission = programmingSubmissionService.findOneWithEagerResultAndFeedback(submissionId);
        Participation participation = programmingSubmission.getParticipation();
        long exerciseId = participation.getExercise().getId();
        ProgrammingExercise programmingExercise = programmingExerciseService.findOne(exerciseId);
        checkAuthorization(programmingExercise, user);
        if (!areManualResultsAllowed(programmingExercise)) {
            return forbidden();
        }

        Result result = programmingAssessmentService.updateAssessmentAfterComplaint(programmingSubmission.getResult(), programmingExercise, assessmentUpdate);

        // remove circular dependencies if the results of the participation are there
        if (result.getParticipation() != null && Hibernate.isInitialized(result.getParticipation().getResults()) && result.getParticipation().getResults() != null) {
            result.getParticipation().setResults(null);
        }

        if (result.getParticipation() != null && result.getParticipation() instanceof StudentParticipation
                && !authCheckService.isAtLeastInstructorForExercise(programmingExercise, user)) {
            ((StudentParticipation) result.getParticipation()).setStudent(null);
        }

        return ResponseEntity.ok(result);
    }

    @Override
    String getEntityName() {
        return ENTITY_NAME;
    }

    private boolean areManualResultsAllowed(final Exercise exerciseToBeChecked) {
        // Only allow manual results for programming exercises if option was enabled and due dates have passed
        final var exercise = (ProgrammingExercise) exerciseToBeChecked;
        final var relevantDueDate = exercise.getBuildAndTestStudentSubmissionsAfterDueDate() != null ? exercise.getBuildAndTestStudentSubmissionsAfterDueDate()
                : exercise.getDueDate();
        return exercise.getAssessmentType() == AssessmentType.SEMI_AUTOMATIC && (relevantDueDate == null || relevantDueDate.isBefore(ZonedDateTime.now()));
    }
}
