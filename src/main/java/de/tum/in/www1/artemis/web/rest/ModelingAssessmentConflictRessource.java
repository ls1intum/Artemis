package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import java.util.*;

import org.slf4j.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.modeling.ModelAssessmentConflict;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.web.rest.errors.ErrorConstants;
import io.swagger.annotations.*;

@Controller
@RequestMapping("/api")
public class ModelingAssessmentConflictRessource {

    private static final String GET_CONFLICTS_200_REASON = "Returns List of all conflicts for given exercise id in body";

    private static final String PUT_ESCALATE_200_REASON = "Escalates conflict of given id";

    private static final String PUT_ESCALATE_BULK_200_REASON = "Escalates conflicts given via request body";

    private final Logger log = LoggerFactory.getLogger(ParticipationResource.class);

    private final AuthorizationCheckService authCheckService;

    private final UserService userService;

    private final ModelAssessmentConflictService conflictService;

    private final ModelingExerciseService modelingExerciseService;

    private final ResultService resultService;

    public ModelingAssessmentConflictRessource(AuthorizationCheckService authCheckService, UserService userService, ModelAssessmentConflictService conflictService,
            ModelingExerciseService modelingExerciseService, ResultService resultService) {
        this.authCheckService = authCheckService;
        this.userService = userService;
        this.conflictService = conflictService;
        this.modelingExerciseService = modelingExerciseService;
        this.resultService = resultService;
    }

    @ResponseStatus(HttpStatus.OK)
    @ApiResponses({ @ApiResponse(code = 403, message = ErrorConstants.REQ_403_REASON), @ApiResponse(code = 404, message = ErrorConstants.REQ_404_REASON),
            @ApiResponse(code = 200, message = GET_CONFLICTS_200_REASON, response = ModelAssessmentConflict.class, responseContainer = "List") })
    @GetMapping("/exercises/{exerciseId}/model-assessment-conflicts")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<ModelAssessmentConflict>> getAllConflicts(@PathVariable Long exerciseId) {
        Exercise exercise = modelingExerciseService.findOne(exerciseId);
        if (authCheckService.isAtLeastInstructorForExercise(exercise)) {
            return forbidden();
        }
        return ResponseEntity.ok(conflictService.getConflictsForExercise(exerciseId));
    }

    @ResponseStatus(HttpStatus.OK)
    @ApiResponses({ @ApiResponse(code = 403, message = ErrorConstants.REQ_403_REASON),
            @ApiResponse(code = 200, message = GET_CONFLICTS_200_REASON, response = ModelAssessmentConflict.class, responseContainer = "List") })
    @GetMapping("/modeling-submissions/{submissionId}/model-assessment-conflicts")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<ModelAssessmentConflict>> getConflictsForSubmission(@PathVariable Long submissionId) {
        return ResponseEntity.ok(conflictService.getConflictsForCurrentUserForSubmission(submissionId));
    }

    @ResponseStatus(HttpStatus.OK)
    @ApiResponses({ @ApiResponse(code = 403, message = ErrorConstants.REQ_403_REASON),
            @ApiResponse(code = 200, message = GET_CONFLICTS_200_REASON, response = ModelAssessmentConflict.class, responseContainer = "List") })
    @GetMapping("/results/{resultId}/model-assessment-conflicts")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<ModelAssessmentConflict>> getConflictsForResult(@PathVariable Long resultId) {
        Result result = resultService.findOne(resultId);
        return ResponseEntity.ok(conflictService.getConflictsForResultInConflict(result));
    }

    @ResponseStatus(HttpStatus.OK)
    @ApiResponses({ @ApiResponse(code = 403, message = ErrorConstants.REQ_403_REASON), @ApiResponse(code = 404, message = ErrorConstants.REQ_404_REASON),
            @ApiResponse(code = 200, message = PUT_ESCALATE_200_REASON, response = ModelAssessmentConflict.class, responseContainer = "List") })
    @PutMapping("/model-assessment-conflicts/{conflictId}/escalate")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity escalateConflict(@PathVariable Long conflictId) {
        Exercise exercise = conflictService.getExerciseOfConflict(conflictId);
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise)) {
            return forbidden();
        }
        else {
            ModelAssessmentConflict conflict = conflictService.findOne(conflictId);
            return ResponseEntity.ok(conflictService.escalateConflict(conflict));
        }
    }

    @ResponseStatus(HttpStatus.OK)
    @ApiResponses({ @ApiResponse(code = 403, message = ErrorConstants.REQ_403_REASON), @ApiResponse(code = 404, message = ErrorConstants.REQ_404_REASON),
            @ApiResponse(code = 200, message = PUT_ESCALATE_BULK_200_REASON, response = ModelAssessmentConflict.class, responseContainer = "List") })
    @PutMapping("/model-assessment-conflicts/escalate")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity escalateConflict(@RequestBody List<ModelAssessmentConflict> conflicts) {
        for (ModelAssessmentConflict conflict : conflicts) {
            Exercise exercise = conflictService.getExerciseOfConflict(conflict.getId());
            if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise)) {
                return forbidden();
            }
        }
        List<ModelAssessmentConflict> escalatedConflicts = new ArrayList<>(conflicts.size());
        for (ModelAssessmentConflict conflict : conflicts) {
            escalatedConflicts.add(conflictService.escalateConflict(conflict));
        }
        return ResponseEntity.ok(escalatedConflicts);
    }

    @PutMapping("/model-assessment-conflicts")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity updateConflict(@RequestBody List<ModelAssessmentConflict> conflicts) {
        User currentUser = userService.getUser();
        for (ModelAssessmentConflict conflict : conflicts) {
            Exercise exercise = conflictService.getExerciseOfConflict(conflict.getId());
            if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise) && !conflictService.userIsResponsibleForHandling(conflict, exercise, currentUser)) {
                return forbidden();
            }
        }
        conflictService.updateEscalatedConflicts(conflicts, currentUser);
        return ResponseEntity.noContent().build();
    }
}
