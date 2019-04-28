package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import java.util.List;

import org.slf4j.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.modeling.ModelAssessmentConflict;
import de.tum.in.www1.artemis.service.*;

@Controller
@RequestMapping("/api")
public class ModelingAssessmentConflictRessource {

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

    @GetMapping("/exercises/{exerciseId}/model-assessment-conflicts")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<ModelAssessmentConflict>> getAllConflicts(@PathVariable Long exerciseId) {
        Exercise exercise = modelingExerciseService.findOne(exerciseId);
        if (authCheckService.isAtLeastInstructorForExercise(exercise)) {
            return forbidden();
        }
        return ResponseEntity.ok(conflictService.getConflictsForExercise(exerciseId));
    }

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

}
