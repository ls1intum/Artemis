package de.tum.in.www1.artemis.repository;

import java.util.Set;

import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import de.tum.in.www1.artemis.domain.ProgrammingExerciseTask;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@SuppressWarnings("unused")
public interface ProgrammingExerciseTaskRepository extends JpaRepository<ProgrammingExerciseTask, Long> {

    Set<ProgrammingExerciseTask> findByExerciseId(Long exerciseId);

    @NotNull
    default ProgrammingExerciseTask findByIdElseThrow(long taskId) throws EntityNotFoundException {
        return findById(taskId).orElseThrow(() -> new EntityNotFoundException("Programming Exercise Task", taskId));
    }

    /**
     * Returns the task name with the given id
     *
     * @param taskId the id of the task
     * @return the name of the task or null if the task does not exist
     */
    @Query("""
            SELECT t.taskName
            FROM ProgrammingExerciseTask t
            WHERE t.id = :taskId
            """)
    String getTaskName(@Param("taskId") Long taskId);
}
