package de.tum.cit.aet.artemis.course.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Collection;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.service.CourseDemoService;

/**
 * API for course functionality that other modules need to access.
 */
@Controller
@Lazy
@Profile(PROFILE_CORE)
public class CourseApi extends AbstractCourseApi {

    private final CourseDemoService courseDemoService;

    public CourseApi(CourseDemoService courseDemoService) {
        this.courseDemoService = courseDemoService;
    }

    /**
     * Creates the demo course if it does not exist yet, creates its missing default channels and enrols the demo users into it.
     *
     * @param students   the demo users enrolled as students.
     * @param tutor      the demo user enrolled as tutor.
     * @param editor     the demo user enrolled as editor.
     * @param instructor the demo user enrolled as instructor.
     * @return the demo course, whether it already existed or was created by this call.
     */
    public Course createDemo(Collection<User> students, User tutor, User editor, User instructor) {
        return courseDemoService.createDemo(students, tutor, editor, instructor);
    }
}
