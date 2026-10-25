package de.tum.cit.aet.artemis.exam.api;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.dto.home.CourseHomeDTO;
import de.tum.cit.aet.artemis.course.dto.home.CourseHomeTargetDTO;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.dto.ExamCourseHomeDTO;
import de.tum.cit.aet.artemis.exam.repository.ExamHomeRepository;

@Controller
@Lazy
@Conditional(ExamEnabled.class)
public class ExamCourseHomeApi extends AbstractExamApi {

    private final ExamHomeRepository exams;

    private final AuthorizationCheckService authorization;

    public ExamCourseHomeApi(ExamHomeRepository exams, AuthorizationCheckService authorization) {
        this.exams = exams;
        this.authorization = authorization;
    }

    public ExamCourseHomeDTO load(long courseId, User user, Instant asOf, Instant since) {
        if (!authorization.isAtLeastStudentInCourse(courseId))
            throw new AccessForbiddenException("course", courseId);
        var now = ZonedDateTime.ofInstant(asOf, ZoneOffset.UTC);
        return new ExamCourseHomeDTO(CourseHomeDTO.SectionStatus.AVAILABLE,
                exams.findUpcoming(courseId, user.getId(), now, now.plusHours(24), PageRequest.of(0, 1)).stream().map(row -> new CourseHomeDTO.Item(row.getTitle(),
                        new CourseHomeTargetDTO(CourseHomeTargetDTO.Kind.EXAM, row.getId(), null, null), null, row.getStartDate().toInstant(), "EXAM_SOON")).toList());
    }
}
