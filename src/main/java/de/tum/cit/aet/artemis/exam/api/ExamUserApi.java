package de.tum.cit.aet.artemis.exam.api;

import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.ExamUser;
import de.tum.cit.aet.artemis.exam.repository.ExamUserRepository;
import de.tum.cit.aet.artemis.exam.service.ExamUserService;

@Conditional(ExamEnabled.class)
@Controller
@Lazy
public class ExamUserApi extends AbstractExamApi {

    private final ExamUserRepository examUserRepository;

    private final ExamUserService examUserService;

    public ExamUserApi(ExamUserRepository examUserRepository, ExamUserService examUserService) {
        this.examUserRepository = examUserRepository;
        this.examUserService = examUserService;
    }

    public Optional<ExamUser> findWithExamById(long examUserId) {
        return examUserRepository.findWithExamById(examUserId);
    }
}
