package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.*;

import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.GradingCriterion;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseParticipationService;
import de.tum.in.www1.artemis.service.programming.ProgrammingSubmissionService;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@RestController
@RequestMapping("/api")
public class ProgrammingExerciseParticipationResource {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseParticipationResource.class);

    private final ParticipationService participationService;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final ResultService resultService;

    private final ProgrammingSubmissionService submissionService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final AuthorizationCheckService authCheckService;

    private final GradingCriterionService gradingCriterionService;

    public ProgrammingExerciseParticipationResource(ProgrammingExerciseParticipationService programmingExerciseParticipationService, ParticipationService participationService,
            ResultService resultService, ProgrammingSubmissionService submissionService, ProgrammingExerciseRepository programmingExerciseRepository,
            AuthorizationCheckService authCheckService, GradingCriterionService gradingCriterionService) {
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.participationService = participationService;
        this.resultService = resultService;
        this.submissionService = submissionService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.authCheckService = authCheckService;
        this.gradingCriterionService = gradingCriterionService;
    }

    /**
     * Get the given student participation with its latest result and feedbacks.
     *
     * @param participationId for which to retrieve the student participation with latest result and feedbacks.
     * @return the ResponseEntity with status 200 (OK) and the participation with its latest result in the body.
     */
    @GetMapping("/programming-exercise-participations/{participationId}/student-participation-with-latest-result-and-feedbacks")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Participation> getParticipationWithLatestResultForStudentParticipation(@PathVariable Long participationId) {
        Optional<ProgrammingExerciseStudentParticipation> participation = programmingExerciseParticipationService
                .findStudentParticipationWithLatestResultAndFeedbacksAndRelatedSubmissions(participationId);
        if (participation.isEmpty()) {
            return notFound();
        }
        if (!programmingExerciseParticipationService.canAccessParticipation(participation.get())) {
            return forbidden();
        }
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(participation.get().getExercise())) {
            // hide details that should not be shown to the students
            participation.get().getExercise().filterSensitiveInformation();

            final boolean isBeforeDueDate = participation.get().getExercise().isBeforeDueDate();
            for (Result result : participation.get().getResults()) {
                result.filterSensitiveInformation();
                result.filterSensitiveFeedbacks(isBeforeDueDate);
            }
        }
        return ResponseEntity.ok(participation.get());
    }

    /**
     * Get the given student participation with the manual result of the given correctionRound and its feedbacks.
     *
     * @param participationId for which to retrieve the student participation with result and feedbacks.
     * @param correctionRound of the result that the participation must have, otherwise notFound is returned
     * @return the ResponseEntity with status 200 (OK) and the participation with its results in the body.
     */
    @GetMapping("/programming-exercise-participations/{participationId}/student-participation-with-result-and-feedbacks-for/{correctionRound}/correction-round")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Participation> getParticipationWithManualResultByCorrectionRoundForStudentParticipation(@PathVariable Long participationId,
            @PathVariable int correctionRound) {
        Optional<ProgrammingExerciseStudentParticipation> participation = programmingExerciseParticipationService
                .findStudentParticipationWithAllManualOrSemiAutomaticResultsAndFeedbacksAndRelatedSubmissionAndAssessor(participationId);
        if (participation.isEmpty()) {
            return notFound();
        }
        if (!programmingExerciseParticipationService.canAccessParticipation(participation.get())) {
            return forbidden();
        }

        // Fetch template and solution participation into exercise of participation
        ProgrammingExercise exercise = (ProgrammingExercise) participation.get().getExercise();
        exercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationByIdElseThrow(exercise.getId());

        // Fetch grading criterion into exercise of participation
        List<GradingCriterion> gradingCriteria = gradingCriterionService.findByExerciseIdWithEagerGradingCriteria(exercise.getId());
        exercise.setGradingCriteria(gradingCriteria);

        // Set exercise back to participation
        participation.get().setExercise(exercise);

        // get the result which belongs to the specific correctionround and set it as the single result in the participation
        List<Result> results = new ArrayList<>(participation.get().getResults());

        // usually this should not be necessary, but just in case the participation's results come in a wrong order this is important
        results.sort((r1, r2) -> r1.getId().compareTo(r2.getId()));

        if (results.size() > correctionRound) {
            Result resultOfCorrectionRound = results.get(correctionRound);
            Set<Result> resultSet = new HashSet<>();

            resultSet.add(resultOfCorrectionRound);
            participation.get().setResults(resultSet);
        }
        else {
            return notFound();
        }

        return ResponseEntity.ok(participation.get());
    }

    /**
     * Get the latest result for a given programming exercise participation including it's result.
     *
     * @param participationId for which to retrieve the programming exercise participation with latest result and feedbacks.
     * @param withSubmission flag determining whether the corresponding submission should also be returned
     * @return the ResponseEntity with status 200 (OK) and the latest result with feedbacks in its body, 404 if the participation can't be found or 403 if the user is not allowed to access the participation.
     */
    @GetMapping(value = "/programming-exercise-participations/{participationId}/latest-result-with-feedbacks")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> getLatestResultWithFeedbacksForProgrammingExerciseParticipation(@PathVariable Long participationId,
            @RequestParam(defaultValue = "false") boolean withSubmission) {
        Participation participation = participationService.findOne(participationId);
        if (!(participation instanceof ProgrammingExerciseParticipation)) {
            return badRequest();
        }
        var programmingParticipation = (ProgrammingExerciseParticipation) participation;

        if (!programmingExerciseParticipationService.canAccessParticipation(programmingParticipation)) {
            return forbidden();
        }

        Optional<Result> result = resultService.findLatestResultWithFeedbacksForParticipation(programmingParticipation.getId(), withSubmission);
        if (result.isPresent() && !authCheckService.isAtLeastTeachingAssistantForExercise(participation.getExercise())) {
            final boolean isBeforeDueDate = participation.getExercise().isBeforeDueDate();
            result.get().filterSensitiveInformation();
            result.get().filterSensitiveFeedbacks(isBeforeDueDate);
        }

        return result.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.ok(null));
    }

    /**
     * Check if the participation has a result yet.
     *
     * @param participationId of the participation to check.
     * @return the ResponseEntity with status 200 (OK) with true if there is a result, otherwise false.
     */
    @GetMapping(value = "/programming-exercise-participations/{participationId}/has-result")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Boolean> checkIfParticipationHashResult(@PathVariable Long participationId) {
        boolean hasResult = resultService.existsByParticipationId(participationId);
        return ResponseEntity.ok(hasResult);
    }

    /**
     * GET /programming-exercise-participation/:id/latest-pending-submission : get the latest pending submission for the participation.
     * A pending submission is one that does not have a result yet.
     *
     * @param participationId the id of the participation get the latest submission for
     * @param lastGraded if true will not try to find the latest pending submission, but the latest GRADED pending submission.
     * @return the ResponseEntity with the last pending submission if it exists or null with status Ok (200). Will return notFound (404) if there is no participation for the given id and forbidden (403) if the user is not allowed to access the participation.
     */
    @GetMapping("/programming-exercise-participations/{participationId}/latest-pending-submission")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ProgrammingSubmission> getLatestPendingSubmission(@PathVariable Long participationId, @RequestParam(defaultValue = "false") boolean lastGraded) {
        Optional<ProgrammingSubmission> submissionOpt;
        try {
            submissionOpt = submissionService.getLatestPendingSubmission(participationId, lastGraded);
        }
        catch (EntityNotFoundException | IllegalArgumentException ex) {
            return notFound();
        }
        // Remove participation, is not needed in the response.
        submissionOpt.ifPresent(submission -> submission.setParticipation(null));
        return ResponseEntity.ok(submissionOpt.orElse(null));
    }

    /**
     * For every student participation of a programming exercise, try to find a pending submission.
     *
     * @param exerciseId for which to search pending submissions.
     * @return a Map of {[participationId]: ProgrammingSubmission | null}. Will contain an entry for every student participation of the exercise and a submission object if a pending submission exists or null if not.
     */
    @GetMapping("/programming-exercises/{exerciseId}/latest-pending-submissions")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Map<Long, Optional<ProgrammingSubmission>>> getLatestPendingSubmissionsByExerciseId(@PathVariable Long exerciseId) {
        ProgrammingExercise programmingExercise;
        try {
            programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationByIdElseThrow(exerciseId);
        }
        catch (EntityNotFoundException ex) {
            return notFound();
        }
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(programmingExercise)) {
            return forbidden();
        }
        Map<Long, Optional<ProgrammingSubmission>> pendingSubmissions = submissionService.getLatestPendingSubmissionsForProgrammingExercise(exerciseId);
        // Remove unnecessary data to make response smaller (exercise, student of participation).
        pendingSubmissions = pendingSubmissions.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> {
            Optional<ProgrammingSubmission> submissionOpt = entry.getValue();
            // Remove participation, is not needed in the response.
            submissionOpt.ifPresent(submission -> submission.setParticipation(null));
            return submissionOpt;
        }));
        return ResponseEntity.ok(pendingSubmissions);
    }
}
