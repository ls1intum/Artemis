package de.tum.cit.aet.artemis.exam.api;

import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;

/**
 * Exposes aggregated exam numbers to callers outside the exam module, namely the public usage dashboard.
 * Only counts are exposed, never who took or created an exam.
 */
@Conditional(ExamEnabled.class)
@Controller
@Lazy
public class ExamUsageStatisticsApi extends AbstractExamApi {

    private final ExamRepository examRepository;

    public ExamUsageStatisticsApi(ExamRepository examRepository) {
        this.examRepository = examRepository;
    }

    /**
     * @param courseIds the courses
     * @return the number of real exams of these courses, without test exams and exams whose title starts with "test" or "mock"
     */
    public long countRealExams(Set<Long> courseIds) {
        return examRepository.countRealExamsInCourses(courseIds);
    }
}
