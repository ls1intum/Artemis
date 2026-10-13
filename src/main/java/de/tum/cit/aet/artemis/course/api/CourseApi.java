package de.tum.cit.aet.artemis.course.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

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

    public Course createDemo(User student, User instructor) {
        return courseDemoService.createDemo(student, instructor);
    }
}
