package de.tum.in.www1.artemis.service.hestia;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.hestia.CodeHint;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseSolutionEntry;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseTask;
import de.tum.in.www1.artemis.repository.hestia.CodeHintRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseSolutionEntryRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseTaskRepository;

@Service
public class CodeHintService {

    private final Logger log = LoggerFactory.getLogger(CodeHintService.class);

    private final CodeHintRepository codeHintRepository;

    private final ProgrammingExerciseTaskRepository taskRepository;

    private final ProgrammingExerciseSolutionEntryRepository solutionEntryRepository;

    public CodeHintService(CodeHintRepository codeHintRepository, ProgrammingExerciseTaskRepository taskRepository,
            ProgrammingExerciseSolutionEntryRepository solutionEntryRepository) {
        this.codeHintRepository = codeHintRepository;
        this.taskRepository = taskRepository;
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
        log.info("Generating code hints for exercise {} with deleteOldCodeHints={}", exercise.getId(), deleteOldCodeHints);

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
        log.info("Generating code hints for task {} ({}) in exercise {} with deleteOldCodeHints={}", task.getId(), task.getTaskName(), task.getExercise().getId(),
                deleteOldCodeHints);

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
        log.info("Deleting all code hints of task {} ({}) in exercise {}", task.getId(), task.getTaskName(), task.getExercise().getId());

        var codeHints = codeHintRepository.findByTaskIdWithSolutionEntries(task.getId());
        var solutionEntries = codeHints.stream().flatMap(codeHint -> codeHint.getSolutionEntries().stream()).peek(solutionEntry -> solutionEntry.setCodeHint(null)).toList();
        solutionEntryRepository.saveAll(solutionEntries);
        codeHintRepository.deleteAll(codeHints);
    }

    /**
     * Deletes a single code hint.
     * Sets the code hint of all related solution entries to null before deleting.
     *
     * @param codeHint The code hint to be deleted
     */
    public void deleteCodeHint(CodeHint codeHint) {
        log.info("Deleting code hint {}", codeHint.getId());

        var solutionEntries = solutionEntryRepository.findByCodeHintId(codeHint.getId());
        for (ProgrammingExerciseSolutionEntry solutionEntry : solutionEntries) {
            solutionEntry.setCodeHint(null);
        }
        solutionEntryRepository.saveAll(solutionEntries);
        codeHintRepository.delete(codeHint);
    }
}
