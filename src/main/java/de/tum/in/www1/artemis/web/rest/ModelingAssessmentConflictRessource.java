package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.modeling.ModelAssessmentConflict;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.ModelAssessmentConflictService;
import de.tum.in.www1.artemis.service.ModelingExerciseService;
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

    private final ModelAssessmentConflictService conflictService;

    private final ModelingExerciseService modelingExerciseService;

    public ModelingAssessmentConflictRessource(AuthorizationCheckService authCheckService, ModelAssessmentConflictService conflictService,
            ModelingExerciseService modelingExerciseService) {
        this.authCheckService = authCheckService;
        this.conflictService = conflictService;
        this.modelingExerciseService = modelingExerciseService;
    }

    /**
     * GET /exercises/:exerciseId/model-assessment-conflicts : sends all conflicts for modeling assessment
     *
     * @param exerciseId the id of the exercise to get the conflicts from
     * @return all conflicts for modelling assessment
     */
    @ResponseStatus(HttpStatus.OK)
    @ApiResponses({ @ApiResponse(code = 403, message = ErrorConstants.REQ_403_REASON), @ApiResponse(code = 404, message = ErrorConstants.REQ_404_REASON),
            @ApiResponse(code = 200, message = GET_CONFLICTS_200_REASON, response = ModelAssessmentConflict.class, responseContainer = "List") })
    @GetMapping("/exercises/{exerciseId}/model-assessment-conflicts")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<ModelAssessmentConflict>> getAllConflicts(@PathVariable Long exerciseId) {
        Exercise exercise = modelingExerciseService.findOne(exerciseId);
        if (!authCheckService.isAtLeastInstructorForExercise(exercise)) {
            return forbidden();
        }
        return ResponseEntity.ok(conflictService.getConflictsForExercise(exerciseId));
    }

    /**
     * PUT model-assessment-conflicts/:conflictId/escalate : updates the state of the given conflict by escalating the conflict to the next authority
     *
     * @param conflictId id of the conflict to escalate
     * @return escalated conflict of the given conflictId
     */
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
            return ResponseEntity.ok(conflictService.escalateConflict(conflictId));
        }
    }

    /**
     * PUT model-assessment-conflicts/escalate : updates the state of the given conflicts by escalating the conflicts to the next authority
     *
     * @param conflicts list of conflicts that will be escalated
     * @return list of escalated conflicts
     */
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
            escalatedConflicts.add(conflictService.escalateConflict(conflict.getId()));
        }
        return ResponseEntity.ok(escalatedConflicts);
    }

}
