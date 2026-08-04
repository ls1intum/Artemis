package de.tum.cit.aet.artemis.course.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseConfiguration;
import de.tum.cit.aet.artemis.course.factories.CourseFactory;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;

/**
 * API for course functionality that other modules need to access.
 */
@Controller
@Lazy
@Profile(PROFILE_CORE)
public class CourseApi extends AbstractCourseApi {

    /**
     * Short name of the demo course. Used as the idempotency key of {@link #createDemo()}: the demo course is identified by this short name alone, so it must stay stable.
     */
    public static final String DEMO_COURSE_SHORT_NAME = "demo";

    private static final String DEMO_COURSE_TITLE = "Artemis Demo Course";

    private static final Logger log = LoggerFactory.getLogger(CourseApi.class);

    private final CourseRepository courseRepository;

    private final ChannelService channelService;

    public CourseApi(CourseRepository courseRepository, ChannelService channelService) {
        this.courseRepository = courseRepository;
        this.channelService = channelService;
    }

    /**
     * Creates the demo course if it does not exist yet, identified by {@link #DEMO_COURSE_SHORT_NAME}.
     * <p>
     * This mirrors the production course creation path (validation, save, default channels) rather than saving the entity directly, so that the demo course behaves like a course
     * created through the UI.
     *
     * @return the demo course, whether it already existed or was created by this call.
     */
    public Course createDemo() {
        List<Course> existingCourses = courseRepository.findAllByShortName(DEMO_COURSE_SHORT_NAME);
        if (!existingCourses.isEmpty()) {
            log.debug("Demo course '{}' already exists, skipping creation", DEMO_COURSE_SHORT_NAME);
            return existingCourses.getFirst();
        }

        ZonedDateTime now = ZonedDateTime.now();
        Course course = CourseFactory.generateCourse(DEMO_COURSE_TITLE, DEMO_COURSE_SHORT_NAME, now.minusMonths(1), now.plusMonths(11), new HashSet<>(), 3, 3, 7, 2000, 2000, true,
                true, 7);
        course.setDescription("Demo course seeded on startup by the 'demo' profile. Feel free to modify it, it is only recreated once it no longer exists.");

        // Mirrors CourseCreateDTO.toCourse(): the retention configuration is attached on creation and defaults to grade-relevant.
        CourseConfiguration configuration = new CourseConfiguration();
        configuration.setGradeRelevant(true);
        configuration.setCourse(course);
        course.setCourseConfiguration(configuration);

        course.validateShortName();
        course.validateEnrollmentConfirmationMessage();
        course.validateComplaintsAndRequestMoreFeedbackConfig();
        course.validateOnlineCourseAndEnrollmentEnabled();
        course.validateAccuracyOfScores();
        course.validatePointBounds();
        course.validateStartAndEndDate();

        Course createdCourse = courseRepository.save(course);
        channelService.createDefaultChannels(createdCourse);

        log.info("Created demo course '{}' with id {}", DEMO_COURSE_SHORT_NAME, createdCourse.getId());
        return createdCourse;
    }
}
