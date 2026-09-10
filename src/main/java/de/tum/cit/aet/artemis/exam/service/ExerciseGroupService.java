package de.tum.cit.aet.artemis.exam.service;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.repository.ExerciseGroupRepository;

@Conditional(ExamEnabled.class)
@Lazy
@Service
public class ExerciseGroupService {

    private static final String ENTITY_NAME = "exerciseGroup";

    private final ExerciseGroupRepository exerciseGroupRepository;

    public ExerciseGroupService(ExerciseGroupRepository exerciseGroupRepository) {
        this.exerciseGroupRepository = exerciseGroupRepository;
    }

    /**
     * Moves an exam exercise into a different exercise group of the same exam.
     * <p>
     * Blocked once a student exam exists: generation has already picked one exercise per group, so a later move would
     * desync those selections and the exam's point totals. The guard sits inside the update statement, so the check
     * and the write cannot be interleaved.
     * <p>
     * Callers must have validated access to the exam and that both the exercise and the target group belong to it.
     *
     * @param examId        the id of the exam both the exercise and the target group belong to
     * @param exerciseId    the id of the exercise to move
     * @param targetGroupId the id of the exercise group to move the exercise into
     * @throws ConflictException if student exams have already been generated for the exam
     */
    public void moveExerciseToGroup(Long examId, Long exerciseId, Long targetGroupId) {
        boolean moved = exerciseGroupRepository.moveToExerciseGroupIfNoStudentExams(exerciseId, targetGroupId, examId);
        if (!moved) {
            throw new ConflictException("The exercise group cannot be changed after student exams have been generated for this exam", ENTITY_NAME, "studentExamsAlreadyGenerated");
        }
    }
}
