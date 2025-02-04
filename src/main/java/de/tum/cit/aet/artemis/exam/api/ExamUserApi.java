package de.tum.cit.aet.artemis.exam.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.exam.domain.ExamUser;
import de.tum.cit.aet.artemis.exam.repository.ExamUserRepository;

@Profile(PROFILE_CORE)
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
