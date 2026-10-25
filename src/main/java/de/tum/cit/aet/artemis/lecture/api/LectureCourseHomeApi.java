package de.tum.cit.aet.artemis.lecture.api;

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
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.dto.LectureCourseHomeDTO;
import de.tum.cit.aet.artemis.lecture.repository.LectureHomeRepository;

@Controller
@Lazy
@Conditional(LectureEnabled.class)
public class LectureCourseHomeApi extends AbstractLectureApi {

    private final LectureHomeRepository lectures;

    private final AuthorizationCheckService authorization;

    public LectureCourseHomeApi(LectureHomeRepository lectures, AuthorizationCheckService authorization) {
        this.lectures = lectures;
        this.authorization = authorization;
    }

    public LectureCourseHomeDTO load(long courseId, User user, Instant asOf, Instant since) {
        if (!authorization.isAtLeastStudentInCourse(courseId))
            throw new AccessForbiddenException("course", courseId);
        var now = ZonedDateTime.ofInstant(asOf, ZoneOffset.UTC);
        var rows = lectures.findUpdates(courseId, user.getId(), now, ZonedDateTime.ofInstant(since, ZoneOffset.UTC), PageRequest.of(0, 3));
        boolean isNew = !rows.isEmpty();
        if (!isNew)
            rows = lectures.findReview(courseId, user.getId(), now, PageRequest.of(0, 1));
        return new LectureCourseHomeDTO(CourseHomeDTO.SectionStatus.AVAILABLE,
                rows.stream().map(row -> new CourseHomeDTO.Item(row.getTitle(), new CourseHomeTargetDTO(CourseHomeTargetDTO.Kind.LECTURE, row.getId(), null, null),
                        row.getPublicationDate() == null ? null : row.getPublicationDate().toInstant(), null, isNew ? "NEW_LECTURE" : "REVIEW")).toList());
    }
}
