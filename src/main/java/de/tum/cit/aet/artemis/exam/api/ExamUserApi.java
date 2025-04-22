package de.tum.cit.aet.artemis.exam.api;

import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.ExamUser;
import de.tum.cit.aet.artemis.exam.repository.ExamUserRepository;

@Conditional(ExamEnabled.class)
@Controller
public class ExamUserApi extends AbstractExamApi {

    private final ExamUserRepository examUserRepository;

    public ExamUserApi(ExamUserRepository examUserRepository) {
        this.examUserRepository = examUserRepository;
    }

    public Optional<ExamUser> findWithExamById(long examUserId) {
        return examUserRepository.findWithExamById(examUserId);
    }
}
