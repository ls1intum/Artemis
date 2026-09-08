package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence;

import java.util.Objects;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseTaskService;

/** Uses the repository-owned transaction to keep metadata and its derived tasks consistent. */
@Lazy
@Service
@Conditional(HyperionExerciseGenerationEnabled.class)
class ProblemStatementMetadataUpdateService {

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseTaskService programmingExerciseTaskService;

    ProblemStatementMetadataUpdateService(ProgrammingExerciseRepository programmingExerciseRepository, ProgrammingExerciseTaskService programmingExerciseTaskService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseTaskService = programmingExerciseTaskService;
    }

    /**
     * Compare-and-set the problem statement/title, then rebuild the exercise's tasks from it, in one transaction: a rebuild failure rolls the metadata write back with it.
     *
     * @param exercise                mutated in memory on success, so the caller's copy reflects the new statement/title
     * @param targetProblemStatement  the problem statement to write
     * @param targetTitle             the title to write
     * @param currentProblemStatement the problem statement expected to currently be persisted
     * @param currentTitle            the title expected to currently be persisted
     * @return the number of updated rows (0 when the compare-and-set guard did not match, 1 on success)
     */
    int updateProblemStatementAndTasks(ProgrammingExercise exercise, String targetProblemStatement, String targetTitle, String currentProblemStatement, String currentTitle) {
        return programmingExerciseRepository.updateProblemStatementAndTitleIfUnchanged(exercise.getId(), targetProblemStatement, targetTitle, currentProblemStatement, currentTitle,
                () -> {
                    if (!Objects.equals(exercise.getTitle(), targetTitle)) {
                        exercise.setTitle(targetTitle);
                    }
                    exercise.setProblemStatement(targetProblemStatement);
                    programmingExerciseTaskService.updateTasksFromProblemStatement(exercise);
                });
    }
}
