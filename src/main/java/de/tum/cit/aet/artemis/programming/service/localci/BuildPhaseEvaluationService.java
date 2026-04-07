package de.tum.cit.aet.artemis.programming.service.localci;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.exercise.service.ExerciseDateService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.dto.BuildPhaseDTO;

/**
 * Service for evaluating build phase conditions at build trigger time.
 * Determines which phases are active based on their conditions and the participation's due date.
 */
@Profile(PROFILE_LOCALCI)
@Lazy
@Service
public class BuildPhaseEvaluationService {

    private final ExerciseDateService exerciseDateService;

    public BuildPhaseEvaluationService(ExerciseDateService exerciseDateService) {
        this.exerciseDateService = exerciseDateService;
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
    public List<BuildPhaseDTO> determineActiveBuildPhases(List<BuildPhaseDTO> phases, ProgrammingExerciseParticipation participation) {
        if (phases == null) {
            return List.of();
        }

        if (isInstructorParticipation(participation)) {
            return phases;
        }

        final boolean isAfterDueDate = exerciseDateService.isAfterDueDate(participation);
        return phases.stream().filter(phase -> isPhaseActive(phase, isAfterDueDate)).toList();
    }

    public static Set<String> gatherResultPaths(List<BuildPhaseDTO> activePhases) {
        if (activePhases == null) {
            return Set.of();
        }
        return activePhases.stream().filter(phase -> phase.resultPaths() != null).flatMap(phase -> phase.resultPaths().stream()).collect(Collectors.toSet());
    }

    private boolean isInstructorParticipation(ProgrammingExerciseParticipation participation) {
        return participation instanceof TemplateProgrammingExerciseParticipation || participation instanceof SolutionProgrammingExerciseParticipation;
    }

    private boolean isPhaseActive(BuildPhaseDTO phase, boolean isAfterDueDate) {
        return switch (phase.condition()) {
            case ALWAYS -> true;
            case AFTER_DUE_DATE -> isAfterDueDate;
        };
    }
}
