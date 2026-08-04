package de.tum.cit.aet.artemis.account.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.account.service.user.UserCreationService;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.service.CourseAccessService;

/**
 * API for user functionality that other modules need to access.
 */
@Controller
@Lazy
@Profile(PROFILE_CORE)
public class UserApi extends AbstractAccountApi {

    /**
     * Login of the demo student. Used as the idempotency key of {@link #createDemo(Course)}, so it must stay stable.
     */
    public static final String DEMO_STUDENT_LOGIN = "demo_student";

    /**
     * Login of the demo instructor. Used as the idempotency key of {@link #createDemo(Course)}, so it must stay stable.
     */
    public static final String DEMO_INSTRUCTOR_LOGIN = "demo_instructor";

    /**
     * Password of every demo user. These credentials are intentionally well known: the demo course only exists on demo and manual testing instances, which are activated through
     * the opt-in {@code demo} profile and must never run in production.
     */
    private static final String DEMO_PASSWORD = "demo1234";

    private static final Logger log = LoggerFactory.getLogger(UserApi.class);

    private final UserRepository userRepository;

    private final UserCreationService userCreationService;

    private final CourseAccessService courseAccessService;

    public UserApi(UserRepository userRepository, UserCreationService userCreationService, CourseAccessService courseAccessService) {
        this.userRepository = userRepository;
        this.userCreationService = userCreationService;
        this.courseAccessService = courseAccessService;
    }

    /**
     * Creates the demo users if they do not exist yet and enrols them into the given course.
     * <p>
     * Both steps are idempotent on their own: users are looked up by login, and enrolment is a no-op when the user already holds the role.
     *
     * @param course the demo course the users are enrolled into.
     */
    public void createDemo(Course course) {
        User student = createDemoUserIfMissing(DEMO_STUDENT_LOGIN, "Demo", "Student");
        User instructor = createDemoUserIfMissing(DEMO_INSTRUCTOR_LOGIN, "Demo", "Instructor");

        courseAccessService.addUserToCourse(student, course, CourseRole.STUDENT);
        courseAccessService.addUserToCourse(instructor, course, CourseRole.INSTRUCTOR);
    }

    private User createDemoUserIfMissing(String login, String firstName, String lastName) {
        return userRepository.findOneByLogin(login).orElseGet(() -> {
            User user = userCreationService.createUser(login, DEMO_PASSWORD, firstName, lastName, login + "@artemis.local", null, null, "en", true);
            // createUser leaves the user deactivated because the regular registration flow activates it via the activation key, which no one is going to click for a demo user.
            user.setActivated(true);
            User activatedUser = userCreationService.saveUser(user);
            log.info("Created demo user '{}'", login);
            return activatedUser;
        });
    }
}
