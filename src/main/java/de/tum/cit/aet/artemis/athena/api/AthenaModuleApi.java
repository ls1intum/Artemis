package de.tum.cit.aet.artemis.athena.api;

import java.util.Optional;

import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.athena.service.AthenaModuleService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

@Controller
public class AthenaModuleApi {

    private final Optional<AthenaModuleService> optionalAthenaModuleService;

    public AthenaModuleApi(Optional<AthenaModuleService> optionalAthenaModuleService) {
        this.optionalAthenaModuleService = optionalAthenaModuleService;
    }

    public boolean isActive() {
        return optionalAthenaModuleService.isPresent();
    }

    public void revokeAccessToRestrictedFeedbackSuggestionModules(Course course) {
        optionalAthenaModuleService.ifPresent(ams -> ams.revokeAccessToRestrictedFeedbackSuggestionModules(course));
    }

    public void checkOrResetModulesUsed(Exercise exercise, Course course, String entityName) throws BadRequestAlertException {
        optionalAthenaModuleService.ifPresentOrElse(ams -> ams.checkHasAccessToAthenaModule(exercise, course, entityName), () -> exercise.setFeedbackSuggestionModule(null));
    }

    public void ensureValidAthenaModuleChange(Exercise originalExercise, Exercise updatedExercise, String entityName) throws BadRequestAlertException {
        optionalAthenaModuleService.ifPresent(athenaModuleService -> athenaModuleService.checkValidAthenaModuleChange(originalExercise, updatedExercise, entityName));
    }
}
