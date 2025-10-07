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
}
