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

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.communication.domain.DefaultChannelType;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseAthenaConfig;
import de.tum.cit.aet.artemis.course.domain.CourseConfiguration;
import de.tum.cit.aet.artemis.course.factories.CourseFactory;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.course.service.CourseAccessService;
import de.tum.cit.aet.artemis.course.service.CourseValidator;

/**
 * API for course functionality that other modules need to access.
 */
@Controller
@Lazy
@Profile(PROFILE_CORE)
public class CourseApi extends AbstractCourseApi {

    /**
     * Short name of the demo course. Used as the idempotency key of {@link #createDemo(User, User)}: the demo course is identified by this short name alone, so it must stay
     * stable.
     */
    public static final String DEMO_COURSE_SHORT_NAME = "demo";

    private static final String DEMO_COURSE_TITLE = "Artemis Demo Course";

    private static final Logger log = LoggerFactory.getLogger(CourseApi.class);

    private final CourseRepository courseRepository;

    private final ChannelService channelService;

    private final ChannelRepository channelRepository;

    private final CourseAccessService courseAccessService;

    public CourseApi(CourseRepository courseRepository, ChannelService channelService, ChannelRepository channelRepository, CourseAccessService courseAccessService) {
        this.courseRepository = courseRepository;
        this.channelService = channelService;
        this.channelRepository = channelRepository;
        this.courseAccessService = courseAccessService;
    }

    /**
     * Creates the demo course if it does not exist yet, identified by {@link #DEMO_COURSE_SHORT_NAME}.
     * <p>
     * This mirrors the production course creation path (validation, save, default channels) rather than saving the entity directly, so that the demo course behaves like a course
     * created through the UI.
     * <p>
     * The demo users are enrolled whether the course is new or not: enrolment is a no-op when the user already holds the role, and a recreated user is enrolled again.
     *
     * @param student    the demo user enrolled as student.
     * @param instructor the demo user enrolled as instructor.
     * @return the demo course, whether it already existed or was created by this call.
     */
    public Course createDemo(User student, User instructor) {
        Course course = findOrCreateDemoCourse();
        createMissingDefaultChannels(course);
        courseAccessService.addUserToCourse(student, course, CourseRole.STUDENT);
        courseAccessService.addUserToCourse(instructor, course, CourseRole.INSTRUCTOR);
        return course;
    }

    private Course findOrCreateDemoCourse() {
        List<Course> existingCourses = courseRepository.findAllByShortName(DEMO_COURSE_SHORT_NAME);
        if (!existingCourses.isEmpty()) {
            log.debug("Demo course '{}' already exists, skipping creation", DEMO_COURSE_SHORT_NAME);
            Course existingCourse = existingCourses.getFirst();
            // Repairs the settings rows of a demo course whose creation was interrupted, the same way a later save through the UI would.
            courseRepository.ensureDefaultConfigurations(existingCourse.getId());
            return existingCourse;
        }

        ZonedDateTime now = ZonedDateTime.now();
        Course course = CourseFactory.generateCourse(DEMO_COURSE_TITLE, DEMO_COURSE_SHORT_NAME, now.minusMonths(1), now.plusMonths(11), new HashSet<>(), 3, 3, 7, 2000, 2000, true,
                true, 7);
        course.setSemester(semesterOf(now));
        course.setDescription("Demo course seeded on startup by the 'demo' profile. Feel free to modify it, it is only recreated once it no longer exists.");

        // Mirrors CourseCreateDTO.toCourse(): Athena starts disabled, and the retention configuration is attached on creation and defaults to grade-relevant.
        course.setAthenaConfig(new CourseAthenaConfig());
        CourseConfiguration configuration = new CourseConfiguration();
        configuration.setGradeRelevant(true);
        configuration.setCourse(course);
        course.setCourseConfiguration(configuration);

        // Mirrors AdminCourseResource#createCourse.
        CourseValidator.validateShortName(course);
        CourseValidator.validateEnrollmentConfirmationMessage(course);
        CourseValidator.validateComplaintsAndRequestMoreFeedbackConfig(course);
        CourseValidator.validateOnlineCourseAndEnrollmentEnabled(course);
        CourseValidator.validateAccuracyOfScores(course);
        CourseValidator.validatePointBounds(course);
        CourseValidator.validateStartAndEndDate(course);
        CourseValidator.validateSemester(course);
        CourseValidator.validateTimeZone(course.getTimeZone());

        Course createdCourse = courseRepository.saveWithDefaultConfigurations(course);

        log.info("Created demo course '{}' with id {}", DEMO_COURSE_SHORT_NAME, createdCourse.getId());
        return createdCourse;
    }

    /**
     * Creates the default channels the demo course is missing. This is checked independently of the course itself, so that channels whose creation failed after the course was
     * saved are created on the next startup, while existing channels are left alone.
     *
     * @param course the demo course.
     */
    private void createMissingDefaultChannels(Course course) {
        for (DefaultChannelType channelType : DefaultChannelType.values()) {
            if (channelRepository.findChannelByCourseIdAndName(course.getId(), channelType.getName()).isEmpty()) {
                channelService.createDefaultChannel(course, channelType);
                log.info("Created default channel '{}' in demo course '{}'", channelType.getName(), DEMO_COURSE_SHORT_NAME);
            }
        }
    }

    /**
     * Formats the semester the given date falls into the way the client expects it: summer semesters (April to September) as {@code SS26}, winter semesters as {@code WS26/27}.
     *
     * @param date the date to derive the semester from.
     * @return the semester of the date.
     */
    private static String semesterOf(ZonedDateTime date) {
        int month = date.getMonthValue();
        if (month >= 4 && month <= 9) {
            return "SS%02d".formatted(date.getYear() % 100);
        }
        int winterStartYear = month <= 3 ? date.getYear() - 1 : date.getYear();
        return "WS%02d/%02d".formatted(winterStartYear % 100, (winterStartYear + 1) % 100);
    }
}
