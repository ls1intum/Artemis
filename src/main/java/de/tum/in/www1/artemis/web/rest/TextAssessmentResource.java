package de.tum.in.www1.artemis.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.TextSubmissionRepository;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.web.rest.dto.FeedbackDTO;
import de.tum.in.www1.artemis.web.rest.dto.TextTutorAssessmentDTO;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
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

    private final ResultService resultService;
    private final TextAssessmentService textAssessmentService;
    private final TextExerciseService textExerciseService;
    private final TextSubmissionRepository textSubmissionRepository;

    public TextAssessmentResource(AuthorizationCheckService authCheckService,
                                  ResultService resultService,
                                  TextAssessmentService textAssessmentService,
                                  TextExerciseService textExerciseService,
                                  TextSubmissionRepository textSubmissionRepository,
                                  UserService userService) {
        super(authCheckService, userService);

        this.resultService = resultService;
        this.textAssessmentService = textAssessmentService;
        this.textExerciseService = textExerciseService;
        this.textSubmissionRepository = textSubmissionRepository;
    }

    @PutMapping("/exercise/{exerciseId}/result/{resultId}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<Result> saveTextAssessment(@PathVariable Long exerciseId, @PathVariable Long resultId, @RequestBody FeedbackDTO textAssessment) {
        TextExercise textExercise = textExerciseService.findOne(exerciseId);
        if (textExercise == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("textExercise", "exerciseNotFound", "No exercise was found for the given ID.")).body(null);
        }
        ResponseEntity responseFailure = checkExercise(textExercise);
        if (responseFailure != null) return responseFailure;

        Result result = textAssessmentService.saveAssessment(resultId, textAssessment.getAssessments());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/exercise/{exerciseId}/submission/{submissionId}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<TextTutorAssessmentDTO> getDataForTutor(@PathVariable Long exerciseId, @PathVariable Long submissionId) {
        TextExercise textExercise = textExerciseService.findOne(exerciseId);
        if (textExercise == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("textExercise", "exerciseNotFound", "No exercise was found for the given ID.")).body(null);
        }
        ResponseEntity responseFailure = checkExercise(textExercise);
        if (responseFailure != null) return responseFailure;

        Optional<TextSubmission> textSubmission = textSubmissionRepository.findById(submissionId);

        if (!textSubmission.isPresent()) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("textSubmission", "textSubmissionNotFound", "No Submission was found for the given ID.")).body(null);
        }

        Result result = resultService.getOrCreateResultForSubmission(textSubmission.get());
        List<Feedback> assessments = textAssessmentService.getAssessmentsForResult(result);

        TextTutorAssessmentDTO response = new TextTutorAssessmentDTO();
        response.setExercise(textExercise);
        response.setSubmission(textSubmission.get());
        response.setAssessments(assessments);
        response.setResult(result);

        return ResponseEntity.ok(response);
    }

    @Override
    String getEntityName() {
        return ENTITY_NAME;
    }

}
