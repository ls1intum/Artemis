package de.tum.cit.aet.artemis.exam.service;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exam.repository.ExerciseGroupRepository;

@Conditional(ExamEnabled.class)
@Lazy
@Service
public class ExerciseGroupService {

    private static final String ENTITY_NAME = "exerciseGroup";

    private final ExamRepository examRepository;

    private final ExerciseGroupRepository exerciseGroupRepository;

    private final TransactionTemplate transactionTemplate;

    public ExerciseGroupService(ExamRepository examRepository, ExerciseGroupRepository exerciseGroupRepository, PlatformTransactionManager transactionManager) {
        this.examRepository = examRepository;
        this.exerciseGroupRepository = exerciseGroupRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Moves an exam exercise into a different exercise group of the same exam.
     * <p>
     * Blocked once a student exam exists: generation has already picked one exercise per group, so a later move would
     * desync those selections and the exam's point totals. The exam row is locked for the duration of the move so a
     * concurrent student exam generation cannot interleave with that guard.
     * <p>
     * Callers must have validated access to the exam and that both the exercise and the target group belong to it.
     *
     * @param examId        the id of the exam both the exercise and the target group belong to
     * @param exerciseId    the id of the exercise to move
     * @param targetGroupId the id of the exercise group to move the exercise into
     * @throws ConflictException if student exams have already been generated for the exam
     */
    public void moveExerciseToGroup(Long examId, Long exerciseId, Long targetGroupId) {
        boolean moved = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            examRepository.findByIdWithPessimisticWriteLockElseThrow(examId);
            return exerciseGroupRepository.moveToExerciseGroupIfNoStudentExams(exerciseId, targetGroupId, examId);
        }));
        if (!moved) {
            throw new ConflictException("The exercise group cannot be changed after student exams have been generated for this exam", ENTITY_NAME, "studentExamsAlreadyGenerated");
        }
    }
}
