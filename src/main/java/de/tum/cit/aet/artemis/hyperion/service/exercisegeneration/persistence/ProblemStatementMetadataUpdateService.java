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
 * Narrowly-scoped database unit that couples the problem-statement/title compare-and-set write with the resulting task rebuild, so the two can never be observed half-applied.
 * <p>
 * {@link ProgrammingExerciseRepository#updateProblemStatementAndTitleIfUnchanged} and {@link ProgrammingExerciseTaskService#updateTasksFromProblemStatement} are each their own
 * separate database round trip (the latter does an unguarded delete-then-saveAll). Without a shared transaction, a failure between the two leaves a committed problem
 * statement/title paired with a half-rebuilt (or stale) task set. This class exists solely so {@code @Transactional} can wrap exactly those two calls and nothing else: no Git
 * operation, CI trigger, or other network I/O happens inside the annotated method, keeping the transaction short-lived as required by the project's narrow-{@code @Transactional}
 * convention (see {@code documentation/docs/developer/guidelines} and {@link de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseImportBasicService} for the
 * broad-scope counter-example this deliberately avoids).
 * <p>
 * Kept as its own Spring bean (rather than a private method on {@link GenerationPersistenceService}) because {@code @Transactional} only takes effect through the Spring AOP
 * proxy; calling an annotated method on {@code this} from within the same class silently skips the transactional advice.
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
     * Updates the problem statement/title (compare-and-set against the currently persisted values) and rebuilds the exercise's tasks from the new problem statement as a single
     * database transaction. If the task rebuild fails after the metadata write, the whole unit rolls back so the caller never has to reconcile a committed statement against a
     * half-deleted task set.
     *
     * @param exercise                the in-memory exercise to mutate on success (so the caller's copy reflects the new statement/title for any later logic in the same call)
     * @param targetProblemStatement  the problem statement to write
     * @param targetTitle             the title to write
     * @param currentProblemStatement the problem statement expected to currently be persisted (CAS guard)
     * @param currentTitle            the title expected to currently be persisted (CAS guard)
     * @return the number of updated rows (0 if the compare-and-set guard did not match; 1 on success)
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
