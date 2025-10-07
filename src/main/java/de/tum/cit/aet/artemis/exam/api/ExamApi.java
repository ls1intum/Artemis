package de.tum.cit.aet.artemis.exam.api;

import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.dto.calendar.CalendarEventDTO;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.dto.StudentExamWithGradeDTO;
import de.tum.cit.aet.artemis.exam.service.ExamService;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;

@Conditional(ExamEnabled.class)
@Controller
@Lazy
public class ExamApi extends AbstractExamApi {

    private final ExamService examService;

    public ExamApi(ExamService examService) {
        this.examService = examService;
    }

    public StudentExamWithGradeDTO getStudentExamGradeForDataExport(StudentExam studentExam) {
        return examService.getStudentExamGradeForDataExport(studentExam);
    }

    public boolean shouldStudentSeeResult(StudentExam studentExam, StudentParticipation participation) {
        return ExamService.shouldStudentSeeResult(studentExam, participation);
    }

    public Set<CalendarEventDTO> getCalendarEventDTOsFromExams(long courseId, boolean userIsStudent, Language language) {
        return examService.getCalendarEventDTOsFromExams(courseId, userIsStudent, language);
    }
}
