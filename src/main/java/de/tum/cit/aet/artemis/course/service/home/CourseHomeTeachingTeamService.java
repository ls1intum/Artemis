package de.tum.cit.aet.artemis.course.service.home;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.dto.home.CourseHomeTeachingTeamDTO;
import de.tum.cit.aet.artemis.course.repository.CourseHomeRepository;

@Service
@Lazy
@Profile(PROFILE_CORE)
public class CourseHomeTeachingTeamService {

    private final CourseHomeRepository courses;

    private final AuthorizationCheckService authorization;

    public CourseHomeTeachingTeamService(CourseHomeRepository courses, AuthorizationCheckService authorization) {
        this.courses = courses;
        this.authorization = authorization;
    }

    public CourseHomeTeachingTeamDTO load(long courseId, int page, int size) {
        if (!authorization.isAtLeastStudentInCourse(courseId))
            throw new AccessForbiddenException("course", courseId);
        if (page < 0 || page > 10000 || (size != 3 && size != 6))
            throw new BadRequestAlertException("Invalid team page", "courseHome", "invalidPage");
        var result = courses.findStaff(courseId, PageRequest.of(page, size));
        return new CourseHomeTeachingTeamDTO(result.stream()
                .map(row -> new CourseHomeTeachingTeamDTO.MemberDTO(row.getId(),
                        de.tum.cit.aet.artemis.account.domain.User.displayName(row.getFirstName(), row.getLastName(), row.getLogin()),
                        de.tum.cit.aet.artemis.core.util.ServedFileUrl.profilePicture(row.getId(), row.getImageUrl()), switch (row.getRank()) {
                            case 0 -> "INSTRUCTOR";
                            case 1 -> "EDITOR";
                            default -> "TEACHING_ASSISTANT";
                        }))
                .toList(), result.hasNext(), page);
    }
}
