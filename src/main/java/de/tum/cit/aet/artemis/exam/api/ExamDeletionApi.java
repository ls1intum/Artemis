package de.tum.cit.aet.artemis.exam.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.exam.service.ExamDeletionService;

@Profile(PROFILE_CORE)
@Controller
public class ExamDeletionApi extends AbstractExamApi {

    private final ExamDeletionService examDeletionService;

    public ExamDeletionApi(ExamDeletionService examDeletionService) {
        this.examDeletionService = examDeletionService;
    }

    public void delete(@NotNull long examId) {
        examDeletionService.delete(examId);
    }
}
