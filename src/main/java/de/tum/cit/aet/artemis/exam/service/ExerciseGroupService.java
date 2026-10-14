package de.tum.cit.aet.artemis.exam.service;

import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exam.repository.ExerciseGroupRepository;
import de.tum.cit.aet.artemis.hyperion.api.HyperionExerciseMutationApi;

@Conditional(ExamEnabled.class)
@Lazy
@Service
public class ExerciseGroupService {

    private static final String ENTITY_NAME = "exerciseGroup";

    private final ExerciseGroupRepository exerciseGroupRepository;

    private final ExamRepository examRepository;

    private final Optional<HyperionExerciseMutationApi> mutationApi;

    public ExerciseGroupService(ExerciseGroupRepository exerciseGroupRepository, ExamRepository examRepository, Optional<HyperionExerciseMutationApi> mutationApi) {
        this.exerciseGroupRepository = exerciseGroupRepository;
        this.examRepository = examRepository;
        this.mutationApi = mutationApi;
    }

    /**
     * Moves an exam exercise into a different exercise group of the same exam.
     * <p>
     * Blocked once a student exam exists: generation has already picked one exercise per group, so a later move would
     * desync those selections and the exam's point totals. When Hyperion protection is enabled, the update shares the exam-row lock with selection and assignment.
     * Otherwise the original update predicate is unchanged.
     * <p>
     * Callers must have validated access to the exam and that both the exercise and the target group belong to it.
     *
     * @param examId        the id of the exam both the exercise and the target group belong to
     * @param exerciseId    the id of the exercise to move
     * @param targetGroupId the id of the exercise group to move the exercise into
     * @throws ConflictException if student exams have already been generated for the exam
     */
    public void moveExerciseToGroup(Long examId, Long exerciseId, Long targetGroupId) {
        boolean moved = mutationApi.map(HyperionExerciseMutationApi::isCoordinationEnabled).orElse(false)
                ? examRepository.withExerciseSelectionLock(examId, exam -> exerciseGroupRepository.moveToExerciseGroupIfNoStudentExams(exerciseId, targetGroupId, examId))
                : exerciseGroupRepository.moveToExerciseGroupIfNoStudentExams(exerciseId, targetGroupId, examId);
        if (!moved) {
            throw new ConflictException("The exercise group cannot be changed after student exams have been generated for this exam", ENTITY_NAME, "studentExamsAlreadyGenerated");
        }
    }
}
