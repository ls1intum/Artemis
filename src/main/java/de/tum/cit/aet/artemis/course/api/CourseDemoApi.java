package de.tum.cit.aet.artemis.course.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_DEMO_AND_SCHEDULING;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.service.ConductAgreementService;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.core.api.AbstractApi;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.service.ResourceLoaderService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseAthenaConfig;
import de.tum.cit.aet.artemis.course.domain.CourseConfiguration;
import de.tum.cit.aet.artemis.course.factories.CourseFactory;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.course.service.CourseAccessService;
import de.tum.cit.aet.artemis.course.service.CourseValidator;

/**
 * Creates the demo course seeded by the {@code demo} profile.
 * <p>
 * Only exists on the node that seeds the demo data, so none of this is instantiated on a regular instance.
 */
@Controller
@Lazy
@Profile(PROFILE_DEMO_AND_SCHEDULING)
public class CourseDemoApi implements AbstractApi {

    /**
     * Short name of the demo course. Used as the idempotency key of {@link #createDemo(Collection, User, User, User)}: the demo course is identified by this short name alone, so
     * it must stay stable.
     */
    public static final String DEMO_COURSE_SHORT_NAME = "demo";

    private static final String DEMO_COURSE_TITLE = "Introduction to Software Engineering";

    private static final String DEMO_COURSE_DESCRIPTION = """
            Learn how to design, build and evaluate software systems: from architectural styles and object-oriented modeling to algorithms and their complexity.
            This demo course was seeded on startup by the 'demo' profile. Feel free to modify it, it is only recreated once it no longer exists.""";

    /**
     * Tutorial groups schedule their sessions in the time zone of their course, so the demo course needs one, see {@code TutorialGroupsConfigurationResource}.
     */
    private static final String DEMO_COURSE_TIME_ZONE = "Europe/Berlin";

    /**
     * The template the client pre-fills the code of conduct of a new course with, see {@code FileResource#getCourseCodeOfConduct}.
     */
    private static final Path CODE_OF_CONDUCT_TEMPLATE = Path.of("templates", "codeofconduct", "README.md");

    private static final Logger log = LoggerFactory.getLogger(CourseDemoApi.class);

    private final CourseRepository courseRepository;

    private final ChannelService channelService;

    private final CourseAccessService courseAccessService;

    private final ConductAgreementService conductAgreementService;

    private final ResourceLoaderService resourceLoaderService;

    public CourseDemoApi(CourseRepository courseRepository, ChannelService channelService, CourseAccessService courseAccessService, ConductAgreementService conductAgreementService,
            ResourceLoaderService resourceLoaderService) {
        this.courseRepository = courseRepository;
        this.channelService = channelService;
        this.courseAccessService = courseAccessService;
        this.conductAgreementService = conductAgreementService;
        this.resourceLoaderService = resourceLoaderService;
    }

    /**
     * Creates the demo course if it does not exist yet, identified by {@link #DEMO_COURSE_SHORT_NAME}, and enrols the demo users into it.
     * <p>
     * This mirrors the production course creation path (validation, save, default channels) rather than saving the entity directly, so that the demo course behaves like a course
     * created through the UI.
     * <p>
     * The demo users are enrolled whether the course is new or not: enrolment is a no-op when the user already holds the role, and a recreated user is enrolled again. They also
     * agree to the code of conduct of the course, so that visitors are not asked to accept it before they can look around.
     *
     * @param students   the demo users enrolled as students.
     * @param tutor      the demo user enrolled as tutor.
     * @param editor     the demo user enrolled as editor.
     * @param instructor the demo user enrolled as instructor.
     * @return the demo course, whether it already existed or was created by this call.
     */
    public Course createDemo(Collection<User> students, User tutor, User editor, User instructor) {
        Course course = findOrCreateDemoCourse();
        students.forEach(student -> courseAccessService.addUserToCourse(student, course, CourseRole.STUDENT));
        courseAccessService.addUserToCourse(tutor, course, CourseRole.TEACHING_ASSISTANT);
        courseAccessService.addUserToCourse(editor, course, CourseRole.EDITOR);
        courseAccessService.addUserToCourse(instructor, course, CourseRole.INSTRUCTOR);
        // The agreement is keyed by course and user, so agreeing again only overwrites the existing agreement.
        Stream.concat(students.stream(), Stream.of(tutor, editor, instructor)).forEach(user -> conductAgreementService.setUserAgreesToCodeOfConductInCourse(user, course));
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
        // Ends after its open exercises and the test exam, which stay open for a year, so that it stays on the dashboards of its users as long as they do.
        Course course = CourseFactory.generateCourse(DEMO_COURSE_TITLE, DEMO_COURSE_SHORT_NAME, now.minusMonths(1), now.plusMonths(13), new HashSet<>(), 3, 3, 7, 2000, 2000, true,
                true, 7);
        course.setSemester(semesterOf(now));
        course.setDescription(DEMO_COURSE_DESCRIPTION);
        course.setTimeZone(DEMO_COURSE_TIME_ZONE);
        // No presentations are seeded, so presentation hints would only point to something that does not exist.
        course.setPresentationScore(0);
        course.setCourseInformationSharingMessagingCodeOfConduct(readCodeOfConductTemplate());
        // The demo course is complete, so the instructor should land on it instead of being sent through the setup wizard for new courses.
        course.setOnboardingDone(true);

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
        channelService.createDefaultChannels(createdCourse);

        log.info("Created demo course '{}' with id {}", DEMO_COURSE_SHORT_NAME, createdCourse.getId());
        return createdCourse;
    }

    /**
     * Reads the code of conduct template the client pre-fills when an instructor creates a course.
     *
     * @return the template, or {@code null} when it cannot be read, in which case the course simply has no code of conduct.
     */
    private @Nullable String readCodeOfConductTemplate() {
        try (InputStream inputStream = resourceLoaderService.getResource(CODE_OF_CONDUCT_TEMPLATE).getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException exception) {
            log.warn("Could not read the code of conduct template for the demo course, creating it without a code of conduct", exception);
            return null;
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
