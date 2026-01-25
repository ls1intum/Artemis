package de.tum.cit.aet.artemis.exam.api;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.dto.ExamStudentCountDTO;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;

@Conditional(ExamEnabled.class)
@Controller
@Lazy
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

    public Set<ExamStudentCountDTO> findExamStudentCountsByCourseIds(Collection<Long> courseId) {
        return examRepository.findExamStudentCountsByCourseIds(courseId);
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
