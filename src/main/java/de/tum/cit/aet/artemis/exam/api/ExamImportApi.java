package de.tum.cit.aet.artemis.exam.api;

import java.io.IOException;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.service.ExamImportService;

/**
 * API for importing exams.
 */
@Conditional(ExamEnabled.class)
@Controller
@Lazy
public class ExamImportApi extends AbstractExamApi {

    private final ExamImportService examImportService;

    public ExamImportApi(ExamImportService examImportService) {
        this.examImportService = examImportService;
    }

    /**
     * Imports an exam with all its exercises to the target course.
     *
     * @param examToCopy     the exam to copy
     * @param targetCourseId the ID of the target course
     * @return the imported exam
     * @throws IOException if an error occurs during import
     */
    public Exam importExamWithExercises(Exam examToCopy, long targetCourseId) throws IOException {
        return examImportService.importExamWithExercises(examToCopy, targetCourseId);
    }
}
