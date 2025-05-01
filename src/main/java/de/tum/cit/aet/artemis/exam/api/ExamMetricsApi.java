package de.tum.cit.aet.artemis.exam.api;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.dto.CourseContentCountDTO;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;

@Conditional(ExamEnabled.class)
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

    public Set<CourseContentCountDTO> countVisibleExams(Set<Long> courseIds, ZonedDateTime now) {
        return examRepository.countVisibleExams(courseIds, now);
    }

    public long countByCourseId(long courseId) {
        return examRepository.countByCourse_Id(courseId);
    }
}
