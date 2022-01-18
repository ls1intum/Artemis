package de.tum.in.www1.artemis.service.programming;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExerciseTask;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTaskRepository;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class ProgrammingExerciseTaskService {

    private final ProgrammingExerciseTaskRepository programmingExerciseTaskRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    public ProgrammingExerciseTaskService(ProgrammingExerciseTaskRepository programmingExerciseTaskRepository, ProgrammingExerciseRepository programmingExerciseRepository) {
        this.programmingExerciseTaskRepository = programmingExerciseTaskRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
    }

    /**
     * Create a programmingExerciseTask
     * @param programmingExerciseTask programming exercise to be created
     * @return the created programming exercise task
     */
    public ProgrammingExerciseTask createProgrammingExerciseTask(ProgrammingExerciseTask programmingExerciseTask) {
        programmingExerciseRepository.findByIdElseThrow(programmingExerciseTask.getExercise().getId());

        return programmingExerciseTaskRepository.save(programmingExerciseTask);
    }

    /**
     * Delete a programmingExerciseTask
     * @param programmingExerciseTaskId id of the task to be deleted
     */
    public void deleteProgrammingExerciseTask(Long programmingExerciseTaskId) {
        var codeHint = programmingExerciseTaskRepository.findById(programmingExerciseTaskId).orElseThrow(() -> new EntityNotFoundException("Code Hint", programmingExerciseTaskId));

        programmingExerciseTaskRepository.delete(codeHint);
    }

    /**
     * Update a programmingExerciseTask
     * @param programmingExerciseTask the updated task
     * @param programmingExerciseTaskId id of the task
     * @return the updated programming exercise task
     */
    public ProgrammingExerciseTask updateProgrammingExerciseTask(ProgrammingExerciseTask programmingExerciseTask, Long programmingExerciseTaskId) {
        if (programmingExerciseTask.getId() == null || !programmingExerciseTaskId.equals(programmingExerciseTask.getId()) || programmingExerciseTask.getExercise() == null) {
            throw new BadRequestAlertException("A programming exercise task can only be changed if it has an ID and if the exercise is not null", "Programming Exercise Task",
                    "idnull");
        }

        return programmingExerciseTaskRepository.save(programmingExerciseTask);
    }
}
