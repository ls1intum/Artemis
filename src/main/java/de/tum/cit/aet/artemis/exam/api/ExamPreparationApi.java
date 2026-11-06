package de.tum.cit.aet.artemis.exam.api;

import java.util.function.Supplier;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.service.ExamPreparationLockService;

/** Mutation-only admission API; student-exam reads do not require participation cleanup infrastructure. */
@Conditional(ExamEnabled.class)
@Controller
@Lazy
public class ExamPreparationApi extends AbstractExamApi {

    private final ExamPreparationLockService examPreparationLockService;

    public ExamPreparationApi(ExamPreparationLockService examPreparationLockService) {
        this.examPreparationLockService = examPreparationLockService;
    }

    /**
     * Coordinates direct exercise setup and resume with exam regeneration and retained external cleanup.
     *
     * @param examId      the exam whose participation is being prepared
     * @param preparation synchronous preparation work
     * @param <T>         the result type
     * @return the preparation result
     */
    public <T> T withParticipationPreparationLock(long examId, Supplier<T> preparation) {
        return examPreparationLockService.withPreparationLock(examId, preparation);
    }
}
