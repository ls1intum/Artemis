package de.tum.cit.aet.artemis.course.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_DEMO_AND_SCHEDULING;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.core.api.AbstractApi;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.service.CourseDemoService;

/**
 * Creates the demo course seeded by the {@code demo} profile.
 * <p>
 * Only exists on the node that seeds the demo data, so none of this is instantiated on a regular instance.
 */
@Controller
@Lazy
@Profile(PROFILE_DEMO_AND_SCHEDULING)
public class CourseDemoApi implements AbstractApi {

    private final CourseDemoService courseDemoService;

    private static final String DEMO_COURSE_TITLE = "Artemis Demo Course";

    private static final Logger log = LoggerFactory.getLogger(CourseDemoApi.class);

    private final CourseRepository courseRepository;

    private final ChannelService channelService;

    private final CourseAccessService courseAccessService;

    public CourseDemoApi(CourseRepository courseRepository, ChannelService channelService, CourseAccessService courseAccessService) {
        this.courseRepository = courseRepository;
        this.channelService = channelService;
        this.courseAccessService = courseAccessService;
    }

    public Course createDemo(User student, User instructor) {
        return courseDemoService.createDemo(student, instructor);
    }
}
