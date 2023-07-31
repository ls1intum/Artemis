package de.tum.in.www1.artemis.config.migration.entries;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import de.tum.in.www1.artemis.config.migration.MigrationEntry;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.hestia.ProgrammingExerciseTaskService;

public class MigrationEntry20230401_150000 extends MigrationEntry {

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseTaskService taskService;

    public MigrationEntry20230401_150000(ProgrammingExerciseRepository programmingExerciseRepository, ProgrammingExerciseTaskService taskService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.taskService = taskService;
    }

    @Override
    public void execute() {
        Pageable pageable = Pageable.ofSize(50);
        Page<ProgrammingExercise> exercises = programmingExerciseRepository.findAll(pageable);
        for (ProgrammingExercise exercise : exercises.getContent()) {
            taskService.replaceTestNamesWithIds(exercise);
        }
        programmingExerciseRepository.saveAll(exercises.getContent());

        while (exercises.hasNext()) {
            exercises = programmingExerciseRepository.findAll(exercises.nextPageable());
            for (ProgrammingExercise exercise : exercises.getContent()) {
                taskService.replaceTestNamesWithIds(exercise);
            }
            programmingExerciseRepository.saveAll(exercises.getContent());
        }
    }

    @Override
    public String author() {
        return "welscher";
    }

    @Override
    public String date() {
        return "20230401_150000";
    }
}
