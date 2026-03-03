package de.tum.cit.aet.artemis.programming.service.localci;

import java.util.List;
import org.springframework.stereotype.Service;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDateService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.dto.BuildPhase;
import de.tum.cit.aet.artemis.programming.dto.BuildPlanPhases;

/**
 * Service for evaluating build phase conditions at build trigger time.
 * Determines which phases are active based on their conditions and the participation's due date.
 */
@Service
public class BuildPhaseEvaluationService {

    private final ExerciseDateService exerciseDateService;

    public BuildPhaseEvaluationService(ExerciseDateService exerciseDateService) {
        this.exerciseDateService = exerciseDateService;
    }

    public record EvaluatedBuildPlan(List<BuildPhase> activePhases, List<String> resultPaths) {
    }

    /**
     * Evaluates which build phases are active for the given participation.
     * <p>
     * For template and solution participations, all phases are always active so that
     * instructors receive full feedback regardless of the exercise due date.
     * <p>
     *
     * @param phases        the build plan phases configuration
     * @param participation the participation for which the build is being triggered
     * @return the evaluated build plan with active phases and result paths
     */
    public EvaluatedBuildPlan evaluate(BuildPlanPhases phases, ProgrammingExerciseParticipation participation) {
        boolean allPhasesActive = isInstructorParticipation(participation) || exerciseDateService.isAfterDueDate(participation);

        List<BuildPhase> activePhases = phases.phases().stream().filter(phase -> isPhaseActive(phase, allPhasesActive)).toList();

        List<String> resultPaths = activePhases.stream().filter(phase -> phase.resultPaths() != null).flatMap(phase -> phase.resultPaths().stream()).toList();

        return new EvaluatedBuildPlan(activePhases, resultPaths);
    }

    private boolean isInstructorParticipation(ProgrammingExerciseParticipation participation) {
        return participation instanceof TemplateProgrammingExerciseParticipation || participation instanceof SolutionProgrammingExerciseParticipation;
    }

    private boolean isPhaseActive(BuildPhase phase, boolean allPhasesActive) {
        return switch (phase.condition()) {
            case ALWAYS -> true;
            case AFTER_DUE_DATE -> allPhasesActive;
        };
    }
}
