package de.tum.in.www1.artemis.config.migration.entries;

import java.util.Arrays;
import java.util.HashSet;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.config.migration.MigrationEntry;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.hestia.CodeHint;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.hestia.ExerciseHintRepository;
import de.tum.in.www1.artemis.service.hestia.ProgrammingExerciseTaskService;

/**
 * This migration extracts the ExerciseHint->ProgrammingExerciseTask mapping from the problem statement and
 * saves it into the ExerciseHint::task property.
 * This also removes the hint declarations from the problem statement
 */
@Component
public class MigrationEntry20220516_180000 extends MigrationEntry {

    private final Pattern taskPatternForProblemStatementMarkdown = Pattern.compile("\\[task]\\[(?<name>[^\\[\\]]+)]\\((?<tests>.*)\\)\\{(?<hintIds>.*)}");

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ExerciseHintRepository exerciseHintRepository;

    private final ProgrammingExerciseTaskService programmingExerciseTaskService;

    public MigrationEntry20220516_180000(ProgrammingExerciseRepository programmingExerciseRepository, ExerciseHintRepository exerciseHintRepository,
            ProgrammingExerciseTaskService programmingExerciseTaskService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.exerciseHintRepository = exerciseHintRepository;
        this.programmingExerciseTaskService = programmingExerciseTaskService;
    }

    @Override
    public void execute() {
        programmingExerciseRepository.findAllWithEagerTasksAndHints().forEach(this::processExercise);
    }

    private void processExercise(ProgrammingExercise exercise) {
        var hints = exercise.getExerciseHints();
        var tasks = exercise.getTasks();

        // Extract the tasks if they have not been extracted yet
        if (tasks.isEmpty()) {
            tasks.addAll(programmingExerciseTaskService.updateTasksFromProblemStatement(exercise));
        }

        var hintIdStrings = new HashSet<String>();

        hints.stream().filter(exerciseHint -> !(exerciseHint instanceof CodeHint)).forEach(textHint -> {
            var matcher = taskPatternForProblemStatementMarkdown.matcher(exercise.getProblemStatement());
            while (matcher.find()) {
                var taskName = matcher.group("name");

                var taskOptional = tasks.stream().filter(task -> task.getTaskName().equals(taskName)).findFirst();
                if (taskOptional.isEmpty()) {
                    continue;
                }
                var task = taskOptional.get();

                var hintIdsString = matcher.group("hintIds");
                hintIdStrings.add("{" + hintIdsString + "}");
                Arrays.stream(hintIdsString.split(",")).map(String::trim).filter(hintId -> hintId.matches("\\d+")).map(Long::parseLong).forEach(hintId -> {
                    if (textHint.getId().equals(hintId)) {
                        textHint.setProgrammingExerciseTask(task);
                        exerciseHintRepository.save(textHint);
                    }
                });

                // Check if the task has already been set
                if (textHint.getProgrammingExerciseTask() != null) {
                    return;
                }
            }
        });

        // Remove all hint definitions
        var problemStatement = exercise.getProblemStatement();
        for (String hintIdString : hintIdStrings) {
            problemStatement = problemStatement.replace(hintIdString, "");
        }
        exercise.setProblemStatement(problemStatement);
        programmingExerciseRepository.save(exercise);
    }

    @Override
    public String author() {
        return "Timor Morrien";
    }

    @Override
    public String date() {
        return "20220516_180000";
    }
}
