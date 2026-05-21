package de.tum.cit.aet.artemis.exam.api;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.service.ExamDeletionService;

@Conditional(ExamEnabled.class)
@Controller
@Lazy
public class ExamDeletionApi extends AbstractExamApi {

    private final ExamDeletionService examDeletionService;

    public ExamDeletionApi(ExamDeletionService examDeletionService) {
        this.examDeletionService = examDeletionService;
    }

    public void deleteByCourseId(long courseId) {
        examDeletionService.deleteByCourseId(courseId);
    }

    /**
     * Deletes an exam and all its associated data including student exams, exercise groups, and exercises.
     *
     * @param examId the ID of the exam to delete
     */
    public void delete(long examId) {
        examDeletionService.delete(examId);
    }

    /**
     * Resets an exam by deleting all student exams and related data while preserving the exam structure.
     *
     * @param examId the ID of the exam to reset
     */
    public void reset(long examId) {
        examDeletionService.reset(examId);
    }
}
