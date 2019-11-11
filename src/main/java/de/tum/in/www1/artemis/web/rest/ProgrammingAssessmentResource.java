package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import java.net.URISyntaxException;
import java.time.ZonedDateTime;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.service.*;

public class ProgrammingAssessmentResource extends AssessmentResource {

    private final Logger log = LoggerFactory.getLogger(ProgrammingAssessmentResource.class);

    private static final String ENTITY_NAME = "programmingAssessment";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ProgrammingExerciseService programmingExerciseService;

    private final ProgrammingAssessmentService programmingAssessmentService;

    private final ResultRepository resultRepository;

    public ProgrammingAssessmentResource(AuthorizationCheckService authCheckService, UserService userService, ProgrammingExerciseService programmingExerciseService,
            ProgrammingAssessmentService programmingAssessmentService, ResultRepository resultRepository) {
        super(authCheckService, userService);
        this.programmingExerciseService = programmingExerciseService;
        this.programmingAssessmentService = programmingAssessmentService;
        this.resultRepository = resultRepository;
    }

    @PutMapping("/manual-results/{resultId}/assessment-after-complaint")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> updateProgrammingExerciseManualResult(@RequestBody AssessmentUpdate assessmentUpdate, @PathVariable Long resultId) throws URISyntaxException {
        log.debug("REST request to update the assessment of manual result {} after complaint.", resultId);
        User user = userService.getUserWithGroupsAndAuthorities();
        Result originalResult = resultRepository.getOne(resultId);
        Participation participation = originalResult.getParticipation();
        long exerciseId = participation.getExercise().getId();
        ProgrammingExercise programmingExercise = programmingExerciseService.findOne(exerciseId);
        checkAuthorization(programmingExercise, user);
        if (!areManualResultsAllowed(programmingExercise)) {
            return forbidden();
        }

        Result result = programmingAssessmentService.updateAssessmentAfterComplaint(originalResult, programmingExercise, assessmentUpdate);

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

    private boolean userHasPermissions(Course course, User user) {
        if (!authCheckService.isTeachingAssistantInCourse(course, user) && !authCheckService.isInstructorInCourse(course, user) && !authCheckService.isAdmin()) {
            return false;
        }
        return true;
    }

    private boolean userHasPermissions(Course course) {
        User user = userService.getUserWithGroupsAndAuthorities();
        return userHasPermissions(course, user);
    }

    private boolean areManualResultsAllowed(final Exercise exerciseToBeChecked) {
        // Only allow manual results for programming exercises if option was enabled and due dates have passed
        if (exerciseToBeChecked instanceof ProgrammingExercise) {
            final var exercise = (ProgrammingExercise) exerciseToBeChecked;
            final var relevantDueDate = exercise.getBuildAndTestStudentSubmissionsAfterDueDate() != null ? exercise.getBuildAndTestStudentSubmissionsAfterDueDate()
                    : exercise.getDueDate();
            return exercise.getAssessmentType() == AssessmentType.SEMI_AUTOMATIC && (relevantDueDate == null || relevantDueDate.isBefore(ZonedDateTime.now()));
        }

        return true;
    }
}
