package de.tum.cit.aet.artemis.programming.service.localci;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.dto.BuildPhase;
import de.tum.cit.aet.artemis.programming.dto.BuildPlanPhases;

/**
 * Service for evaluating build phase conditions at build trigger time.
 * Determines which phases are active based on their conditions and the exercise's due date.
 */
@Service
public class BuildPhaseEvaluationService {

    public record EvaluatedBuildPlan(List<BuildPhase> activePhases, boolean testsExpected, List<String> resultPaths) {
    }

    public EvaluatedBuildPlan evaluate(BuildPlanPhases phases, ProgrammingExercise exercise) {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime dueDate = exercise.getDueDate();

        List<BuildPhase> activePhases = phases.phases().stream().filter(phase -> isPhaseActive(phase, now, dueDate)).toList();

        List<String> resultPaths = activePhases.stream().filter(phase -> phase.resultPaths() != null).flatMap(phase -> phase.resultPaths().stream()).toList();

        boolean testsExpected = !resultPaths.isEmpty();

        return new EvaluatedBuildPlan(activePhases, testsExpected, resultPaths);
    }

    private boolean isPhaseActive(BuildPhase phase, ZonedDateTime now, ZonedDateTime dueDate) {
        return switch (phase.condition()) {
            case ALWAYS -> true;
            case AFTER_DUE_DATE -> dueDate != null && now.isAfter(dueDate);
        };
    }
}
