package de.tum.cit.aet.artemis.athena.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_ATHENA;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.athena.service.AthenaModuleService;
import de.tum.cit.aet.artemis.athena.service.AthenaScheduleService;
import de.tum.cit.aet.artemis.athena.service.AthenaSubmissionSelectionService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

@Profile(PROFILE_ATHENA)
@Controller
public class AthenaApi extends AbstractAthenaApi {

    private final AthenaModuleService athenaModuleService;

    private final AthenaScheduleService athenaScheduleService;

    private final AthenaSubmissionSelectionService athenaSubmissionSelectionService;

    public AthenaApi(AthenaModuleService athenaModuleService, AthenaScheduleService athenaScheduleService, AthenaSubmissionSelectionService athenaSubmissionSelectionService) {
        this.athenaModuleService = athenaModuleService;
        this.athenaScheduleService = athenaScheduleService;
        this.athenaSubmissionSelectionService = athenaSubmissionSelectionService;
    }

    public void scheduleExerciseForAthenaIfRequired(Exercise exercise) {
        athenaScheduleService.scheduleExerciseForAthenaIfRequired(exercise);
    }

    public void cancelScheduledAthena(Long exerciseId) {
        athenaScheduleService.cancelScheduledAthena(exerciseId);
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
