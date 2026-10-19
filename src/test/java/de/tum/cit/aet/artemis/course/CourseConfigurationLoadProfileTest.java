package de.tum.cit.aet.artemis.course;

import static de.tum.cit.aet.artemis.core.util.QueryCountAssert.assertThatDb;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseAthenaConfig;
import de.tum.cit.aet.artemis.course.domain.CourseConfiguration;
import de.tum.cit.aet.artemis.course.dto.CourseManagementDTO;
import de.tum.cit.aet.artemis.course.repository.CourseAthenaConfigRepository;
import de.tum.cit.aet.artemis.course.repository.CourseConfigurationRepository;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.util.TextExerciseFactory;

/**
 * Pins what reading a course costs.
 * <p>
 * The Athena configuration and the course configuration hold the key to their course, and the course carries no mapped
 * association to either, so loading a course reads the course row and nothing else. This pins that, because it is easy
 * to lose: a 2000 student benchmark read {@code course_athena_config} a quarter of a million times while the
 * identically annotated course configuration - whose foreign key is set on every course - was read thirty-five times.
 * The mapping was not the cause, which is what this test established; the reads came from queries that fetch it and
 * from code that touches the association. With the key on the configuration there is nothing left to fetch or touch, so
 * the tests below look at the statements themselves rather than at their number: no read of a course, an exam or an
 * exercise may mention either table, and the flows that do need them read each one exactly once.
 */
class CourseConfigurationLoadProfileTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "courseloadprofile";

    // a word boundary, because online_course_configuration is another table that contains the name
    private static final Pattern ATHENA_CONFIG_TABLE = Pattern.compile("\\bcourse_athena_config\\b");

    private static final Pattern COURSE_CONFIGURATION_TABLE = Pattern.compile("\\bcourse_configuration\\b");

    @Autowired
    private CourseTestRepository courseRepository;

    @Autowired
    private ExamTestRepository examRepository;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private CourseAthenaConfigRepository courseAthenaConfigRepository;

    @Autowired
    private CourseConfigurationRepository courseConfigurationRepository;

    private long courseId;

    private long examId;

    private long exerciseId;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
        Course course = courseUtilService.createEnrolledCourse(TEST_PREFIX);
        CourseAthenaConfig athenaConfig = new CourseAthenaConfig();
        athenaConfig.setGradingFeedbackEnabled(true);
        course.setAthenaConfig(athenaConfig);
        CourseConfiguration configuration = new CourseConfiguration();
        configuration.setGradeRelevant(false);
        course.setCourseConfiguration(configuration);
        courseId = courseUtilService.saveWithConfigurations(course).getId();

        Exam exam = examUtilService.addExamWithExerciseGroup(courseRepository.findByIdElseThrow(courseId), true);
        examId = exam.getId();
        TextExercise examExercise = TextExerciseFactory.generateTextExerciseForExam(exam.getExerciseGroups().getFirst());
        exerciseId = exerciseRepository.save(examExercise).getId();
    }

    @Test
    void readingACourseDoesNotReadItsConfigurations() throws Exception {
        assertThatDb(() -> courseRepository.findByIdElseThrow(courseId)).hasBeenCalledTimes(1);
        assertThat(tablesRead(() -> courseRepository.findByIdElseThrow(courseId))).isEmpty();
    }

    @Test
    void readingAnExamReadsTheExamAndItsCourseOnly() throws Exception {
        // Exam.course is an eager @ManyToOne, so two rows; the course's configurations must stay out of it
        assertThatDb(() -> examRepository.findByIdElseThrow(examId)).hasBeenCalledAtMostTimes(2);
        assertThat(tablesRead(() -> examRepository.findByIdElseThrow(examId))).isEmpty();
    }

    @Test
    void readingAnExamExerciseReadsTheChainAndNothingElse() throws Exception {
        // Exercise.exerciseGroup, ExerciseGroup.exam and Exam.course are all eager @ManyToOne, so the chain is read
        // whatever we do. What must not be added on top is the Athena configuration of either course.
        assertThatDb(() -> exerciseRepository.findByIdElseThrow(exerciseId)).hasBeenCalledAtMostTimes(4);
        assertThat(tablesRead(() -> exerciseRepository.findByIdElseThrow(exerciseId))).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void aStudentReadingTheCourseDoesNotReadItsConfigurations() throws Exception {
        assertThat(tablesRead(() -> request.get("/api/course/courses/" + courseId, HttpStatus.OK, CourseManagementDTO.class))).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void theCourseDropdownDoesNotReadTheConfigurationsOfAnyCourse() throws Exception {
        assertThat(tablesRead(() -> request.get("/api/course/courses/for-dropdown", HttpStatus.OK, String.class))).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void anInstructorReadingTheCourseReadsEachConfigurationExactlyOnce() throws Exception {
        List<String> statements = statementsOf(() -> request.get("/api/course/courses/" + courseId, HttpStatus.OK, CourseManagementDTO.class));

        assertThat(statements.stream().filter(sql -> ATHENA_CONFIG_TABLE.matcher(sql).find())).as("reads of course_athena_config").hasSize(1);
        assertThat(statements.stream().filter(sql -> COURSE_CONFIGURATION_TABLE.matcher(sql).find())).as("reads of course_configuration").hasSize(1);

        // and what they read reaches the response, which is what the instructor edits
        CourseManagementDTO course = request.get("/api/course/courses/" + courseId, HttpStatus.OK, CourseManagementDTO.class);
        assertThat(course.athenaGradingFeedbackEnabled()).isTrue();
        assertThat(course.courseConfiguration()).isNotNull();
        assertThat(course.courseConfiguration().gradeRelevant()).isFalse();
    }

    @Test
    void deletingTheCourseDeletesBothConfigurations() {
        Course bare = courseUtilService.createCourse();
        CourseAthenaConfig athenaConfig = new CourseAthenaConfig();
        bare.setAthenaConfig(athenaConfig);
        bare.setCourseConfiguration(new CourseConfiguration());
        long bareId = courseUtilService.saveWithConfigurations(bare).getId();
        assertThat(courseAthenaConfigRepository.findByCourseId(bareId)).isPresent();
        assertThat(courseConfigurationRepository.findByCourseId(bareId)).isPresent();

        // the configurations hold the key to their course, so the database removes them with it
        courseRepository.deleteById(bareId);

        assertThat(courseAthenaConfigRepository.findByCourseId(bareId)).isEmpty();
        assertThat(courseConfigurationRepository.findByCourseId(bareId)).isEmpty();
    }

    @Test
    void aConfigurationWithoutACourseCannotBeWritten() {
        assertThatThrownBy(() -> courseAthenaConfigRepository.saveAndFlush(new CourseAthenaConfig())).as("an Athena configuration without a course").isNotNull();
        assertThatThrownBy(() -> courseConfigurationRepository.saveAndFlush(new CourseConfiguration())).as("a course configuration without a course").isNotNull();
    }

    @Test
    void bulkAttachingConfigurationsCostsOneQuery() throws Exception {
        Course second = courseUtilService.createCourse();
        second.setCourseConfiguration(new CourseConfiguration());
        second = courseUtilService.saveWithConfigurations(second);
        List<Course> courses = List.of(courseRepository.findByIdElseThrow(courseId), courseRepository.findByIdElseThrow(second.getId()));

        assertThatDb(() -> {
            courseConfigurationRepository.attachTo(courses);
            return courses;
        }).hasBeenCalledTimes(1);

        assertThat(courses).allSatisfy(course -> assertThat(course.getCourseConfiguration()).isNotNull());
    }

    /**
     * Runs a call and returns the configuration tables it read.
     *
     * @param call the call to run
     * @return the names of the configuration tables mentioned by the statements the call executed; empty if it read neither
     */
    private List<String> tablesRead(ThrowingCall call) throws Exception {
        List<String> statements = statementsOf(call);
        return List.of("course_athena_config", "course_configuration").stream()
                .filter(table -> statements.stream().anyMatch(sql -> Pattern.compile("\\b" + table + "\\b").matcher(sql).find())).toList();
    }

    private List<String> statementsOf(ThrowingCall call) throws Exception {
        queryInterceptor.startQueryCount();
        call.run();
        return queryInterceptor.getStatements();
    }

    @FunctionalInterface
    private interface ThrowingCall {

        void run() throws Exception;
    }
}
