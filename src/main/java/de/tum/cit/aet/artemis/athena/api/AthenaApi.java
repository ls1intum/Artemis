package de.tum.cit.aet.artemis.athena.api;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.athena.config.AthenaEnabled;
import de.tum.cit.aet.artemis.athena.service.AthenaModuleService;
import de.tum.cit.aet.artemis.athena.service.AthenaScheduleService;
import de.tum.cit.aet.artemis.athena.service.AthenaSubmissionSelectionService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

@Conditional(AthenaEnabled.class)
@Controller
@Lazy
public class AthenaApi extends AbstractAthenaApi {

    private final AthenaModuleService athenaModuleService;

    private final Optional<AthenaScheduleService> athenaScheduleService;

    private final AthenaSubmissionSelectionService athenaSubmissionSelectionService;

    public AthenaApi(AthenaModuleService athenaModuleService, Optional<AthenaScheduleService> athenaScheduleService,
            AthenaSubmissionSelectionService athenaSubmissionSelectionService) {
        this.athenaModuleService = athenaModuleService;
        this.athenaScheduleService = athenaScheduleService;
        this.athenaSubmissionSelectionService = athenaSubmissionSelectionService;
    }

    public void scheduleExerciseForAthenaIfRequired(Exercise exercise) {
        athenaScheduleService.ifPresent(service -> service.scheduleExerciseForAthenaIfRequired(exercise));
    }

    public void cancelScheduledAthena(Long exerciseId) {
        athenaScheduleService.ifPresent(service -> service.cancelScheduledAthena(exerciseId));
    }

    public Optional<Long> getProposedSubmissionId(Exercise exercise, List<Long> submissionIds) {
        return athenaSubmissionSelectionService.getProposedSubmissionId(exercise, submissionIds);
    }

    public void checkHasAccessToAthenaModule(Exercise exercise, Course course, String entityName) throws BadRequestAlertException {
        athenaModuleService.checkHasAccessToAthenaModule(exercise, course, entityName);
    }

    public void checkValidAthenaModuleChange(Exercise originalExercise, Exercise updatedExercise, String entityName) throws BadRequestAlertException {
        athenaModuleService.checkValidAthenaModuleChange(originalExercise, updatedExercise, entityName);
    }

    public void revokeAccessToRestrictedFeedbackSuggestionModules(Course course) {
        athenaModuleService.revokeAccessToRestrictedFeedbackSuggestionModules(course);
    }
}
