package de.tum.cit.aet.artemis.exam.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.dto.CourseContentCount;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;

@Profile(PROFILE_CORE)
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

    public Set<CourseContentCount> countVisibleExams(Set<Long> courseIds, ZonedDateTime now) {
        return examRepository.countVisibleExams(courseIds, now);
    }

    public long countByCourseId(long courseId) {
        return examRepository.countByCourse_Id(courseId);
    }
}
