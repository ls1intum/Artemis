package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence;

import java.util.Objects;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseTaskService;

/**
 * Couples the problem-statement/title compare-and-set write with the task rebuild it drives, so the two can never be observed half-applied.
 * <p>
 * {@link ProgrammingExerciseRepository#updateProblemStatementAndTitleIfUnchanged} and {@link ProgrammingExerciseTaskService#updateTasksFromProblemStatement} are separate database
 * round trips, the latter an unguarded delete-then-saveAll, so without a shared transaction a failure between them leaves a committed statement paired with a half-rebuilt task
 * set. This class exists solely so {@code @Transactional} can wrap exactly those two calls: no Git operation, CI trigger, or other network I/O may enter the annotated method,
 * keeping the transaction short-lived as the project's narrow-{@code @Transactional} convention requires (see {@code documentation/docs/developer/guidelines}).
 * <p>
 * It must stay its own Spring bean rather than a private method on {@link GenerationPersistenceService}: {@code @Transactional} takes effect only through the AOP proxy, so a
 * same-class call would silently skip the advice.
 */
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
    @Transactional
    int updateProblemStatementAndTasks(ProgrammingExercise exercise, String targetProblemStatement, String targetTitle, String currentProblemStatement, String currentTitle) {
        int updatedRows = programmingExerciseRepository.updateProblemStatementAndTitleIfUnchanged(exercise.getId(), targetProblemStatement, targetTitle, currentProblemStatement,
                currentTitle);
        if (updatedRows == 1) {
            if (!Objects.equals(exercise.getTitle(), targetTitle)) {
                exercise.setTitle(targetTitle);
            }
            exercise.setProblemStatement(targetProblemStatement);
            programmingExerciseTaskService.updateTasksFromProblemStatement(exercise);
        }
        return updatedRows;
    }
}
