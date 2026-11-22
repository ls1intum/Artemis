package de.tum.cit.aet.artemis.exam.api;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.course.domain.Course;
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

    /**
     * Loads an exam user.
     *
     * @param examUserId the id of the exam user
     * @return the exam user
     * @throws EntityNotFoundException if the exam user does not exist
     */
    public ExamUser findByIdElseThrow(long examUserId) {
        return examUserRepository.findByIdElseThrow(examUserId);
    }

    /**
     * Loads the course of the exam an exam user is registered for.
     *
     * @param examUserId the id of the exam user
     * @return the course of the exam
     * @throws EntityNotFoundException if the exam user does not exist
     */
    public Course findCourseByExamUserIdElseThrow(long examUserId) {
        return examUserRepository.findCourseByExamUserId(examUserId).orElseThrow(() -> new EntityNotFoundException("ExamUser", examUserId));
    }
}
