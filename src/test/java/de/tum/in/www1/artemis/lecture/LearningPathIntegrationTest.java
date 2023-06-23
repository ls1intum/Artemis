package de.tum.in.www1.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.competency.CompetencyUtilService;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.competency.LearningPath;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.service.LearningPathService;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.PageableSearchUtilService;

class LearningPathIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "learningpathintegration";

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private LearningPathService learningPathService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    CompetencyUtilService competencyUtilService;

    @Autowired
    PageableSearchUtilService pageableSearchUtilService;

    private Course course;

    private Competency[] competencies;

    private final int NUMBER_OF_STUDENTS = 5;

    private final String STUDENT_OF_COURSE = TEST_PREFIX + "student1";

    private final String TUTOR_OF_COURSE = TEST_PREFIX + "tutor1";

    private final String EDITOR_OF_COURSE = TEST_PREFIX + "editor1";

    private final String INSTRUCTOR_OF_COURSE = TEST_PREFIX + "instructor1";

    @BeforeEach
    void setupTestScenario() {
        participantScoreScheduleService.activate();

        userUtilService.addUsers(TEST_PREFIX, NUMBER_OF_STUDENTS, 1, 1, 1);

        // Add users that are not in the course
        userUtilService.createAndSaveUser(TEST_PREFIX + "student1337");
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor1337");

        // creating course
        course = courseUtilService.createCourse();

        competencies = competencyUtilService.createCompetencies(course, 5);

    }

    private void enableLearningPathsForTestingCourse() {
        course.setLeanringPathsEnabled(true);
        learningPathService.generateLearningPaths(course);
        course = courseRepository.save(course);
    }

    private ZonedDateTime now() {
        return ZonedDateTime.now();
    }

    private ZonedDateTime past(long days) {
        return now().minusDays(days);
    }

    private ZonedDateTime future(long days) {
        return now().plusDays(days);
    }

    private void testAllPreAuthorize() throws Exception {
        request.putWithResponseBody("/api/courses/" + course.getId() + "/learning-paths/enable", null, Course.class, HttpStatus.FORBIDDEN);
        final var search = pageableSearchUtilService.configureSearch("");
        request.getSearchResult("/api/courses/" + course.getId() + "/learning-paths", HttpStatus.FORBIDDEN, LearningPath.class, pageableSearchUtilService.searchMapping(search));
    }

    @Test
    @WithMockUser(username = STUDENT_OF_COURSE, roles = "USER")
    void testAll_asStudent() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = TUTOR_OF_COURSE, roles = "TA")
    void testAll_asTutor() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = EDITOR_OF_COURSE, roles = "EDITOR")
    void testAll_asEditor() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_OF_COURSE, roles = "INSTRUCTOR")
    void testEnableLearningPaths() throws Exception {
        request.putWithResponseBody("/api/courses/" + course.getId() + "/learning-paths/enable", course, Course.class, HttpStatus.OK);
        final var updatedCourse = courseRepository.findByIdElseThrow(course.getId());
        assertThat(updatedCourse.getLearningPathsEnabled()).isTrue().as("should enable LearningPaths");
        assertThat(updatedCourse.getLearningPaths()).isNotNull();
        assertThat(updatedCourse.getLearningPaths().size()).isEqualTo(NUMBER_OF_STUDENTS).as("should create LearningPath for each student");
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_OF_COURSE, roles = "INSTRUCTOR")
    void testEnableLearningPathsAlreadyEnabled() throws Exception {
        course.setLeanringPathsEnabled(true);
        courseRepository.save(course);
        request.putWithResponseBody("/api/courses/" + course.getId() + "/learning-paths/enable", course, Course.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1337", roles = "USER")
    void testGenerateLearningPathOnEnrollment() throws Exception {
        course.setEnrollmentEnabled(true);
        course.setEnrollmentStartDate(past(1));
        course.setEnrollmentEndDate(future(1));
        course = courseRepository.save(course);
        enableLearningPathsForTestingCourse();
        final var updatedStudent = request.postWithResponseBody("/api/courses/" + course.getId() + "/enroll", null, User.class, HttpStatus.OK);
        assertThat(updatedStudent.getLearningPaths()).isNotNull();
        assertThat(updatedStudent.getLearningPaths().size()).isEqualTo(1).as("should create LearningPath for student");
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_OF_COURSE, roles = "INSTRUCTOR")
    void testGetLearningPathsOnPageForCourseLearningPathsDisabled() throws Exception {
        final var search = pageableSearchUtilService.configureSearch("");
        request.getSearchResult("/api/courses/" + course.getId() + "/learning-paths", HttpStatus.BAD_REQUEST, LearningPath.class, pageableSearchUtilService.searchMapping(search));
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_OF_COURSE, roles = "INSTRUCTOR")
    void testGetLearningPathsOnPageForCourseEmpty() throws Exception {
        enableLearningPathsForTestingCourse();
        final var search = pageableSearchUtilService.configureSearch(STUDENT_OF_COURSE + "SuffixThatAllowsTheResultToBeEmpty");
        final var result = request.getSearchResult("/api/courses/" + course.getId() + "/learning-paths", HttpStatus.OK, LearningPath.class,
                pageableSearchUtilService.searchMapping(search));
        assertThat(result.getResultsOnPage()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_OF_COURSE, roles = "INSTRUCTOR")
    void testGetLearningPathsOnPageForCourseExactlyStudent() throws Exception {
        enableLearningPathsForTestingCourse();
        final var search = pageableSearchUtilService.configureSearch(STUDENT_OF_COURSE);
        final var result = request.getSearchResult("/api/courses/" + course.getId() + "/learning-paths", HttpStatus.OK, LearningPath.class,
                pageableSearchUtilService.searchMapping(search));
        assertThat(result.getResultsOnPage()).hasSize(1);
    }
}
