package de.tum.cit.aet.artemis.exam.api;

import java.nio.file.Path;
import java.util.List;
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

    /**
     * Deletes a user's exam registrations as part of explicitly confirmed permanent account deletion.
     *
     * @param userId the user to remove from exams
     * @return personal file paths that the caller must delete after its transaction commits
     */
    public List<Path> deleteAllForUser(long userId) {
        return examUserService.deleteAllForUser(userId);
    }
}
