package de.tum.cit.aet.artemis.exam.api;

import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exam.repository.ExamUserRepository;

/**
 * Exposes aggregated exam numbers to callers outside the exam module, namely the public usage dashboard.
 * Only counts are exposed, never who took or created an exam.
 */
@Conditional(ExamEnabled.class)
@Controller
@Lazy
public class ExamUsageStatisticsApi extends AbstractExamApi {

    private final ExamRepository examRepository;

    private final ExamUserRepository examUserRepository;

    public ExamUsageStatisticsApi(ExamRepository examRepository, ExamUserRepository examUserRepository) {
        this.examRepository = examRepository;
        this.examUserRepository = examUserRepository;
    }

    /**
     * @param courseIds the courses
     * @return the number of registrations of students for the real exams of these courses. A student registered for several exams is counted once per exam.
     */
    public long countRealExamRegistrations(Set<Long> courseIds) {
        return examUserRepository.countRegistrationsForRealExamsInCourses(courseIds);
    }

    /**
     * @param courseIds the courses
     * @return the number of real exams of these courses, without test exams and exams whose title starts with "test" or "mock"
     */
    public long countRealExams(Set<Long> courseIds) {
        return examRepository.countRealExamsInCourses(courseIds);
    }
}
