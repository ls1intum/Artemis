package de.tum.in.www1.artemis.config.migration.entries;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import de.tum.in.www1.artemis.config.migration.MigrationEntry;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.hestia.ProgrammingExerciseTaskService;

/**
 * Migration for automatically updating all Programming exercise problem statements, removing their test names and replacing them by test ids.
 */
public class MigrationEntry20230810_150000 extends MigrationEntry {

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseTaskService taskService;

    public MigrationEntry20230810_150000(ProgrammingExerciseRepository programmingExerciseRepository, ProgrammingExerciseTaskService taskService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.taskService = taskService;
    }

    @Override
    public void execute() {
        Pageable pageable = Pageable.ofSize(50);
        while (true) {
            Page<ProgrammingExercise> exercisePage = programmingExerciseRepository.findAll(pageable);
            for (ProgrammingExercise exercise : exercisePage.getContent()) {
                taskService.replaceTestNamesWithIds(exercise);
            }
            programmingExerciseRepository.saveAll(exercisePage.getContent());
            if (exercisePage.isLast()) {
                break;
            }
            pageable = pageable.next();
        }
    }

    @Override
    public String author() {
        return "welscher";
    }

    @Override
    public String date() {
        return "20230810_150000";
    }
}
