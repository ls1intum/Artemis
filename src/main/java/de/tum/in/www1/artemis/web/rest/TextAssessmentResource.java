package de.tum.in.www1.artemis.web.rest;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.TextSubmissionRepository;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * REST controller for managing TextAssessment.
 */
@RestController
@RequestMapping("/api/text-assessments")
public class TextAssessmentResource extends AssessmentResource {

    private final Logger log = LoggerFactory.getLogger(TextAssessmentResource.class);

    private static final String ENTITY_NAME = "textAssessment";

    private final ParticipationService participationService;
    private final ResultService resultService;
    private final TextAssessmentService textAssessmentService;
    private final TextExerciseService textExerciseService;
    private final TextSubmissionRepository textSubmissionRepository;
    private final ResultRepository resultRepository;

    public TextAssessmentResource(AuthorizationCheckService authCheckService,
                                  ParticipationService participationService,
                                  ResultService resultService,
                                  TextAssessmentService textAssessmentService,
                                  TextExerciseService textExerciseService,
                                  TextSubmissionRepository textSubmissionRepository,
                                  ResultRepository resultRepository,
                                  UserService userService) {
        super(authCheckService, userService);

        this.participationService = participationService;
        this.resultService = resultService;
        this.textAssessmentService = textAssessmentService;
        this.textExerciseService = textExerciseService;
        this.textSubmissionRepository = textSubmissionRepository;
        this.resultRepository = resultRepository;
    }

    @PutMapping("/exercise/{exerciseId}/result/{resultId}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> saveTextAssessment(@PathVariable Long exerciseId, @PathVariable Long resultId, @RequestBody List<Feedback> textAssessments) {
        TextExercise textExercise = textExerciseService.findOne(exerciseId);
        ResponseEntity<Result> responseFailure = checkTextExerciseForRequest(textExercise);
        if (responseFailure != null) return responseFailure;

        Result result = textAssessmentService.saveAssessment(resultId, textAssessments);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/exercise/{exerciseId}/result/{resultId}/submit")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> submitTextAssessment(@PathVariable Long exerciseId, @PathVariable Long resultId, @RequestBody List<Feedback> textAssessments) {
        TextExercise textExercise = textExerciseService.findOne(exerciseId);
        ResponseEntity<Result> responseFailure = checkTextExerciseForRequest(textExercise);
        if (responseFailure != null) return responseFailure;

        Result result = textAssessmentService.submitAssessment(resultId, textExercise, textAssessments);
        return ResponseEntity.ok(result);
    }

    /**
     * Given an exerciseId and a submissionId, the method retrieves from the database all the data needed by the tutor
     * to assess the submission.
     *
     * If the tutor has already started assessing the submission, then we also return all the results the tutor has
     * already inserted.
     *
     * If another tutor has already started working on this submission, the system returns an error
     *
     * @param exerciseId the id of the exercise we want the submission
     * @param submissionId the id of the submission we want
     * @return a Participation of the tutor in the submission
     */
    @GetMapping("/exercise/{exerciseId}/submission/{submissionId}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Participation> retrieveParticipationForSubmission(@PathVariable Long exerciseId, @PathVariable Long submissionId) {
        log.debug("REST request to get data for tutors text assessment: {}", exerciseId, submissionId);
        TextExercise textExercise = textExerciseService.findOne(exerciseId);
        ResponseEntity<Participation> responseFailure = checkTextExerciseForRequest(textExercise);
        if (responseFailure != null) return responseFailure;

        Optional<TextSubmission> textSubmission = textSubmissionRepository.findById(submissionId);
        if (!textSubmission.isPresent()) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("textSubmission", "textSubmissionNotFound", "No Submission was found for the given ID.")).body(null);
        }

        Participation participation = textSubmission.get().getParticipation();
        participation = participationService.findOneWithEagerResultsAndSubmissions(participation.getId());

        if (!participation.getResults().isEmpty()) {
            User user = userService.getUser();
            //TODO: check that the latest submission is not null
            User assessor = participation.findLatestSubmission().get().getResult().getAssessor();
            // Another tutor started assessing this submission.
            if (!assessor.getLogin().equals(user.getLogin())) {
                throw new BadRequestAlertException("This submission is being assessed by another tutor", ENTITY_NAME, "alreadyAssessed");
            }
        }

        if (participation.getResults().isEmpty()) {
            Result result = new Result();
            result.setParticipation(participation);
            result.setSubmission(textSubmission.get());
            resultService.createNewResult(result, false);
            participation.addResult(result);
        }

        for (Result result : participation.getResults()) {
            List<Feedback> assessments = textAssessmentService.getAssessmentsForResult(result);
            result.setFeedbacks(assessments);
        }

        return ResponseEntity.ok(participation);
    }

    /**
     * Retrieve the result of an example assessment, only if the user is an instructor or if the submission is used for
     * tutorial purposes.
     *
     * @param exerciseId the id of the exercise
     * @param submissionId the id of the submission
     * @return the result linked to the submission
     */
    @GetMapping("/exercise/{exerciseId}/submission/{submissionId}/exampleAssessment")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> getExampleAssessmentForTutor(@PathVariable Long exerciseId, @PathVariable Long submissionId) {
        log.debug("REST request to get example assessment for tutors text assessment: {}", submissionId);
        Optional<TextSubmission> textSubmission = textSubmissionRepository.findById(submissionId);
        TextExercise textExercise = textExerciseService.findOne(exerciseId);
        if (!textSubmission.isPresent()) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("textSubmission", "textSubmissionNotFound", "No Submission was found for the given ID.")).body(null);
        }

        // If the user is not an instructor, and this is not an example submission used for tutorial,
        // do not provide the results
        boolean isAtLeastInstructor = authCheckService.isAtLeastInstructorForExercise(textExercise);
        if (!textSubmission.get().isExampleSubmission() && !isAtLeastInstructor) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("textSubmission", "notAuthorized", "You cannot see results")).body(null);
        }

        Optional<Result> databaseResult = this.resultRepository.findDistinctBySubmissionId(submissionId);
        Result result = databaseResult.orElseGet(() -> {
            Result newResult = new Result();
            newResult.setSubmission(textSubmission.get());
            newResult.setExampleResult(true);
            resultService.createNewResult(newResult, false);
            return newResult;
        });

        List<Feedback> assessments = textAssessmentService.getAssessmentsForResult(result);
        result.setFeedbacks(assessments);

        return ResponseEntity.ok(result);
    }

    @Override
    String getEntityName() {
        return ENTITY_NAME;
    }

    @Nullable
    private <X> ResponseEntity<X> checkTextExerciseForRequest(TextExercise textExercise) {
        if (textExercise == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("textExercise", "exerciseNotFound", "No exercise was found for the given ID.")).body(null);
        }
        return checkExercise(textExercise);
    }

}
