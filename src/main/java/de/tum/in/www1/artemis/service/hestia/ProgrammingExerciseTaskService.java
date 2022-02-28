package de.tum.in.www1.artemis.service.hestia;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseTask;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.repository.hestia.CodeHintRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseTaskRepository;

@Service
public class ProgrammingExerciseTaskService {

    private ProgrammingExerciseTaskRepository programmingExerciseTaskRepository;

    private ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    private CodeHintRepository codeHintRepository;

    /**
     * Pattern that is used to extract the tasks (capturing group "name") and test case names (capturing group "tests") from the problem statement.
     * Example: "[task][Implement BubbleSort](testBubbleSort,testBubbleSortHidden)". Following values are extracted by the named capturing groups:
     * - name: "Implement BubbleSort"
     * - tests: "testBubbleSort,testBubbleSortHidden"
     */
    private final Pattern taskPatternForProblemStatementMarkdown = Pattern.compile("\\[task]\\[(?<name>[^\\[\\]]+)]\\((?<tests>.*)\\)");

    public ProgrammingExerciseTaskService(ProgrammingExerciseTaskRepository programmingExerciseTaskRepository,
            ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository, CodeHintRepository codeHintRepository) {
        this.programmingExerciseTaskRepository = programmingExerciseTaskRepository;
        this.programmingExerciseTestCaseRepository = programmingExerciseTestCaseRepository;
        this.codeHintRepository = codeHintRepository;
    }

    /**
     * Deletes a ProgrammingExerciseTask together with its CodeHints
     * This has to be manually done, as there is no orphanRemoval between the two entities
     *
     * @param task The task to delete
     */
    public void delete(ProgrammingExerciseTask task) {
        var codeHints = codeHintRepository.findByTaskId(task.getId());
        codeHintRepository.deleteAll(codeHints);
        programmingExerciseTaskRepository.delete(task);
    }

    /**
     * Extracts all tasks from the problem statement of an exercise and saves them to the database.
     * All tasks that no longer exist will be deleted.
     * If there is already a task with the same test cases as a new one, but with a different name the existing one will be renamed.
     *
     * @param exercise The programming exercise to extract the tasks from
     */
    public void updateTasksFromProblemStatement(ProgrammingExercise exercise) {
        var previousTasks = programmingExerciseTaskRepository.findByExerciseIdWithTestCases(exercise.getId());
        var extractedTasks = extractTasks(exercise);
        // No changes
        if (previousTasks.equals(extractedTasks)) {
            return;
        }
        // Add all tasks that did not change
        var tasksToBeSaved = new HashSet<>(previousTasks);
        tasksToBeSaved.retainAll(extractedTasks);
        var tasksToBeDeleted = new HashSet<>(previousTasks);
        tasksToBeDeleted.removeAll(tasksToBeSaved);
        var tasksToBeAdded = new HashSet<>(extractedTasks);
        tasksToBeAdded.removeAll(tasksToBeSaved);
        // Check for tasks where only the name changed
        Iterator<ProgrammingExerciseTask> extractedTaskIterator = tasksToBeAdded.iterator();
        while (extractedTaskIterator.hasNext()) {
            ProgrammingExerciseTask extractedTask = extractedTaskIterator.next();
            Iterator<ProgrammingExerciseTask> previousTaskIterator = tasksToBeDeleted.iterator();
            while (previousTaskIterator.hasNext()) {
                ProgrammingExerciseTask previousTask = previousTaskIterator.next();
                if (previousTask.getTestCases().equals(extractedTask.getTestCases())) {
                    previousTask.setTaskName(extractedTask.getTaskName());
                    tasksToBeSaved.add(previousTask);
                    extractedTaskIterator.remove();
                    previousTaskIterator.remove();
                    break;
                }
            }
        }
        // Add all newly created tasks
        tasksToBeSaved.addAll(tasksToBeAdded);
        // Remove old tasks
        for (ProgrammingExerciseTask task : tasksToBeDeleted) {
            delete(task);
        }
        // Save all tasks
        for (ProgrammingExerciseTask task : tasksToBeSaved) {
            task.setExercise(exercise);
            programmingExerciseTaskRepository.save(task);
        }
    }

    /**
     * Returns the extracted tasks and test cases from the problem statement markdown and
     * maps the tasks to the corresponding test cases for a programming exercise
     * @param exercise the exercise for which the tasks and test cases should be extracted
     * @return the extracted tasks with the corresponding test cases
     */
    private Set<ProgrammingExerciseTask> extractTasks(ProgrammingExercise exercise) {
        var problemStatement = exercise.getProblemStatement();
        var matcher = taskPatternForProblemStatementMarkdown.matcher(problemStatement);
        var testCases = programmingExerciseTestCaseRepository.findByExerciseIdAndActive(exercise.getId(), true);
        var tasks = new HashSet<ProgrammingExerciseTask>();
        while (matcher.find()) {
            var taskName = matcher.group("name");
            var testCaseNames = matcher.group("tests");

            var task = new ProgrammingExerciseTask();
            task.setTaskName(taskName);
            task.setExercise(exercise);
            String[] testNames = testCaseNames.split(",");
            for (String testName : testNames) {
                String finalTestName = testName.trim();
                testCases.stream().filter(tc -> tc.getTestName().equals(finalTestName)).findFirst().ifPresent(task.getTestCases()::add);
            }
            tasks.add(task);
        }
        return tasks;
    }
}
