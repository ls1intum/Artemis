package de.tum.cit.aet.artemis.athena.api;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.athena.config.AthenaEnabled;
import de.tum.cit.aet.artemis.athena.service.AthenaScheduleService;
import de.tum.cit.aet.artemis.athena.service.AthenaSubmissionSelectionService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

@Conditional(AthenaEnabled.class)
@Controller
@Lazy
public class AthenaApi extends AbstractAthenaApi {

    private final Optional<AthenaScheduleService> athenaScheduleService;

    private final AthenaSubmissionSelectionService athenaSubmissionSelectionService;

    public AthenaApi(Optional<AthenaScheduleService> athenaScheduleService, AthenaSubmissionSelectionService athenaSubmissionSelectionService) {
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

}
