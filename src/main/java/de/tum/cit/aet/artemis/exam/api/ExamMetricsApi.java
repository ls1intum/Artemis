package de.tum.cit.aet.artemis.exam.api;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;

@ConditionalOnProperty(name = "artemis.exam.enabled", havingValue = "true")
@Controller
public class ExamMetricsApi extends AbstractExamApi {

    private final ExamRepository examRepository;

    public ExamMetricsApi(ExamRepository examRepository) {
        this.examRepository = examRepository;
    }

    public Integer countExamsWithEndDateBetween(ZonedDateTime minDate, ZonedDateTime maxDate) {
        return examRepository.countExamsWithEndDateBetween(minDate, maxDate);
    }

    public Integer countExamUsersInExamsWithEndDateBetween(ZonedDateTime minDate, ZonedDateTime maxDate) {
        return examRepository.countExamUsersInExamsWithEndDateBetween(minDate, maxDate);
    }

    public Integer countExamsWithStartDateBetween(ZonedDateTime minDate, ZonedDateTime maxDate) {
        return examRepository.countExamsWithStartDateBetween(minDate, maxDate);
    }

    public Integer countExamUsersInExamsWithStartDateBetween(ZonedDateTime minDate, ZonedDateTime maxDate) {
        return examRepository.countExamUsersInExamsWithStartDateBetween(minDate, maxDate);
    }

    public List<Exam> findExamsInCourses(Iterable<Long> courseId) {
        return examRepository.findExamsInCourses(courseId);
    }

    public Integer countAllActiveExams(ZonedDateTime now) {
        return examRepository.countAllActiveExams(now);
    }

    public long count() {
        return examRepository.count();
    }

    public long countByCourseId(long courseId) {
        return examRepository.countByCourse_Id(courseId);
    }
}
