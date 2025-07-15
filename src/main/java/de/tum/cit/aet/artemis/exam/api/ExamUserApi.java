package de.tum.cit.aet.artemis.exam.api;

import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.exam.domain.ExamUser;
import de.tum.cit.aet.artemis.exam.repository.ExamUserRepository;

@ConditionalOnProperty(name = "artemis.exam.enabled", havingValue = "true")
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
