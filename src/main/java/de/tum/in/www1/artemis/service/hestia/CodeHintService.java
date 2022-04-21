package de.tum.in.www1.artemis.service.hestia;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.hestia.CodeHint;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseSolutionEntry;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseTask;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.repository.hestia.CodeHintRepository;
import de.tum.in.www1.artemis.repository.hestia.ExerciseHintRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseSolutionEntryRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseTaskRepository;

@Service
public class CodeHintService {

    private final CodeHintRepository codeHintRepository;

    private final ExerciseHintRepository exerciseHintRepository;

    private final ProgrammingExerciseTaskRepository taskRepository;

    private final ProgrammingExerciseTestCaseRepository testCaseRepository;

    private final ProgrammingExerciseSolutionEntryRepository solutionEntryRepository;

    public CodeHintService(CodeHintRepository codeHintRepository, ExerciseHintRepository exerciseHintRepository, ProgrammingExerciseTaskRepository taskRepository,
            ProgrammingExerciseTestCaseRepository testCaseRepository, ProgrammingExerciseSolutionEntryRepository solutionEntryRepository) {
        this.codeHintRepository = codeHintRepository;
        this.exerciseHintRepository = exerciseHintRepository;
        this.taskRepository = taskRepository;
        this.testCaseRepository = testCaseRepository;
        this.solutionEntryRepository = solutionEntryRepository;
    }

    /**
     * Generate {@link CodeHint}s for all {@link ProgrammingExerciseTask}s of an exercise.
     * If requested old code hints will be deleted otherwise the new ones will be added to the existing ones. (This might however break the old ones)
     * If a task does not have any test cases with solution entries it will not get a code hint.
     *
     * @param exercise           The programming exercise
     * @param deleteOldCodeHints Whether old code hint should be deleted
     * @return The list of all newly generated code hints
     */
    public List<CodeHint> generateCodeHintsForExercise(ProgrammingExercise exercise, boolean deleteOldCodeHints) {
        var tasks = taskRepository.findByExerciseIdWithTestCaseAndSolutionEntriesElseThrow(exercise.getId());

        return tasks.stream().map(task -> generateCodeHintForTask(task, deleteOldCodeHints)).filter(Optional::isPresent).map(Optional::get).toList();
    }

    /**
     * Generate a single {@link CodeHint} for a single {@link ProgrammingExerciseTask}
     * If requested old code hints will be deleted otherwise the new one will be added to the existing ones. (This might however break the old ones)
     * If the task does not have any test cases with solution entries it will not get a code hint.
     *
     * @param task               The programming exercise task
     * @param deleteOldCodeHints Whether old code hint should be deleted
     * @return The newly created code hint if one was needed
     */
    public Optional<CodeHint> generateCodeHintForTask(ProgrammingExerciseTask task, boolean deleteOldCodeHints) {
        var codeHint = new CodeHint();
        codeHint.setExercise(task.getExercise());
        codeHint.setProgrammingExerciseTask(task);
        codeHint.setTitle("Code hint for task " + task.getTaskName());

        var solutionEntries = task.getTestCases().stream().flatMap(testCase -> testCase.getSolutionEntries().stream()).peek(solutionEntry -> solutionEntry.setCodeHint(codeHint))
                .collect(Collectors.toSet());

        if (deleteOldCodeHints) {
            deleteCodeHintsForTask(task);
        }
        if (solutionEntries.isEmpty()) {
            return Optional.empty();
        }

        codeHint.setSolutionEntries(solutionEntries);
        codeHintRepository.save(codeHint);
        solutionEntryRepository.saveAll(solutionEntries);

        return Optional.of(codeHint);
    }

    /**
     * Deletes all code hints of a {@link ProgrammingExerciseTask}
     *
     * @param task The programming exercise task
     */
    public void deleteCodeHintsForTask(ProgrammingExerciseTask task) {
        var codeHints = codeHintRepository.findByTaskIdWithSolutionEntries(task.getId());
        var solutionEntries = codeHints.stream().flatMap(codeHint -> codeHint.getSolutionEntries().stream()).peek(solutionEntry -> solutionEntry.setCodeHint(null)).toList();
        solutionEntryRepository.saveAll(solutionEntries);
        codeHintRepository.deleteAll(codeHints);
    }

    public void deleteCodeHint(CodeHint codeHint) {
        var solutionEntries = solutionEntryRepository.findByCodeHintId(codeHint.getId());
        for (ProgrammingExerciseSolutionEntry solutionEntry : solutionEntries) {
            solutionEntry.setCodeHint(null);
        }
        solutionEntryRepository.saveAll(solutionEntries);
        codeHintRepository.delete(codeHint);
    }
}
